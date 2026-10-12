package ru.rtumirea.repairly.fx.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import ru.rtumirea.repairly.fx.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.service.RequestFilter;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public final class RequestsController {
    private record Option<T>(String label, T value) {}
    @FXML private TextField search;
    @FXML private ComboBox<Option<RepairStatus>> statusFilter;
    @FXML private ComboBox<Option<Integer>> technicianFilter;
    @FXML private DatePicker fromDate, toDate;
    @FXML private TableView<RequestView> table;
    @FXML private Label count, filterError, linkedFilter, detailTitle, detailClient, detailTechnician, detailDates;
    @FXML private TextArea detailProblem;
    @FXML private Button editButton, deleteButton, addButton, exportButton;
    private Workspace workspace;
    private FilteredList<RequestView> filtered;
    private Integer clientId;

    public void configure(Workspace workspace) {
        this.workspace = workspace;
        filterError.managedProperty().bind(filterError.textProperty().isNotEmpty());
        filterError.visibleProperty().bind(filterError.managedProperty());
        linkedFilter.managedProperty().bind(linkedFilter.textProperty().isNotEmpty());
        configureColumns();
        filtered = new FilteredList<>(workspace.requestRows);
        SortedList<RequestView> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(new Label("Заявок не найдено. Измените фильтры или создайте заявку."));
        count.textProperty().bind(Bindings.createStringBinding(
                () -> "Показано " + filtered.size() + " из " + workspace.requestRows.size(), filtered, workspace.requestRows));
        statusFilter.setConverter(Ui.converter(Option::label));
        statusFilter.getItems().add(new Option<>("Все статусы", null));
        for (var status : RepairStatus.values()) statusFilter.getItems().add(new Option<>(status.label(), status));
        statusFilter.getSelectionModel().selectFirst();
        technicianFilter.setConverter(Ui.converter(Option::label));
        updateTechnicians();
        search.textProperty().addListener((obs, old, value) -> applyFilters());
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilters());
        technicianFilter.valueProperty().addListener((obs, old, value) -> applyFilters());
        fromDate.valueProperty().addListener((obs, old, value) -> applyFilters());
        toDate.valueProperty().addListener((obs, old, value) -> applyFilters());
        // Calendar-only input prevents partially typed dates from silently becoming a different date.
        fromDate.setEditable(false); toDate.setEditable(false);
        var selection = table.getSelectionModel().selectedItemProperty();
        deleteButton.disableProperty().bind(selection.isNull());
        editButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selection.get() == null || selection.get().request().status().isClosed(), selection));
        addButton.disableProperty().bind(Bindings.isEmpty(workspace.clientRows));
        addButton.setTooltip(new Tooltip("Для регистрации заявки сначала добавьте клиента в разделе «Клиенты»."));
        selection.addListener((obs, old, row) -> showDetails(row));
        table.setRowFactory(ignored -> {
            TableRow<RequestView> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && !row.getItem().request().status().isClosed()) edit();
            });
            return row;
        });
        filtered.addListener((ListChangeListener<RequestView>) change -> {
            if (table.getSelectionModel().getSelectedItem() == null) showDetails(null);
        });
        workspace.revision.addListener((obs, old, value) -> {
            updateTechnicians();
            applyFilters();
            if (table.getSelectionModel().getSelectedItem() == null && !table.getItems().isEmpty()) table.getSelectionModel().selectFirst();
        });
        showDetails(null);
    }

    private void configureColumns() {
        TableColumn<RequestView, Integer> number = Ui.column("№", 65, r -> r.request().id());
        TableColumn<RequestView, RepairStatus> status = Ui.column("Статус", 155, r -> r.request().status());
        status.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(RepairStatus value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) { setGraphic(null); return; }
                Label badge = new Label(value.label());
                badge.getStyleClass().addAll("status-badge", "status-" + value.name().toLowerCase(Locale.ROOT));
                setGraphic(badge);
            }
        });
        TableColumn<RequestView, LocalDateTime> date = Ui.column("Принята", 150, r -> r.request().createdAt());
        Ui.dates(date);
        date.setSortType(TableColumn.SortType.DESCENDING);
        TableColumn<RequestView, BigDecimal> cost = Ui.column("Стоимость", 125, r -> r.request().cost());
        cost.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : Ui.money(value));
            }
        });
        table.getColumns().setAll(List.of(number, Ui.column("Техника", 245, RequestView::appliance),
                Ui.column("Клиент", 185, r -> r.client().name()), status, date, cost,
                Ui.column("Мастер", 180, RequestView::technicianName)));
        table.getSortOrder().setAll(List.of(date, number));
    }

    private void updateTechnicians() {
        Integer previous = technicianFilter.getValue() == null ? null : technicianFilter.getValue().value();
        List<Option<Integer>> options = new ArrayList<>();
        options.add(new Option<>("Все мастера", null));
        options.add(new Option<>("Не назначен", 0));
        workspace.technicianRows.forEach(t -> options.add(new Option<>(t.name(), t.id())));
        technicianFilter.getItems().setAll(options);
        technicianFilter.setValue(options.stream().filter(o -> Objects.equals(o.value(), previous)).findFirst().orElse(options.getFirst()));
    }

    private void applyFilters() {
        try {
            var status = statusFilter.getValue();
            var technician = technicianFilter.getValue();
            RequestFilter filter = new RequestFilter(search.getText(), status == null ? null : status.value(),
                    technician == null ? null : technician.value(), fromDate.getValue(), toDate.getValue());
            filtered.setPredicate(r -> filter.test(r) && (clientId == null || r.request().clientId() == clientId));
            filterError.setText("");
            exportButton.setDisable(false);
        } catch (ru.rtumirea.repairly.exception.ValidationException error) {
            filterError.setText(error.getMessage());
            filtered.setPredicate(row -> false);
            exportButton.setDisable(true);
        }
    }

    @FXML private void clearFilters() {
        clientId = null;
        linkedFilter.setText("");
        search.clear();
        fromDate.setValue(null); toDate.setValue(null);
        statusFilter.getSelectionModel().selectFirst();
        technicianFilter.getSelectionModel().selectFirst();
        applyFilters();
    }

    public void showClient(int id) {
        clearFilters();
        clientId = id;
        workspace.clientRows.stream().filter(c -> c.id() == id).findFirst()
                .ifPresent(c -> linkedFilter.setText("Заявки клиента: " + c.name() + " · «Сбросить» для всех заявок"));
        applyFilters();
    }

    public void showTechnician(int id) {
        clearFilters();
        technicianFilter.getItems().stream().filter(o -> Objects.equals(o.value(), id)).findFirst().ifPresent(technicianFilter::setValue);
    }

    private void showDetails(RequestView row) {
        if (row == null) {
            detailTitle.setText("Выберите заявку в таблице");
            detailClient.setText("Здесь появятся контакты клиента и сведения о ремонте.");
            detailTechnician.setText(""); detailDates.setText(""); detailProblem.clear();
            return;
        }
        var r = row.request();
        detailTitle.setText("Заявка №" + r.id() + " · " + row.appliance());
        detailClient.setText(row.client().name() + " · " + row.client().phone() + "\n" + row.client().address());
        detailTechnician.setText(row.technician() == null ? "Мастер ещё не назначен" : row.technician().name()
                + " · " + row.technician().phone() + "\n" + row.technician().specialization());
        detailDates.setText(r.status().label() + " · принята " + Ui.DATE.format(r.createdAt())
                + (r.completedAt() == null ? "" : " · завершена " + Ui.DATE.format(r.completedAt()))
                + (r.status().isClosed() ? "\nЗакрытая заявка доступна только для просмотра и удаления." : ""));
        detailProblem.setText(r.problem());
    }

    @FXML private void add() { open(null); }
    @FXML private void edit() {
        var selected = table.getSelectionModel().getSelectedItem();
        if (selected != null && !selected.request().status().isClosed()) open(selected);
    }

    private void open(RequestView row) {
        var view = Ui.<RequestDialogController>load("request-dialog.fxml");
        var stage = Ui.dialog(workspace.owner, row == null ? "Новая заявка" : "Заявка №" + row.request().id(), view.root());
        view.controller().configure(workspace, stage, row);
        stage.showAndWait();
    }

    @FXML private void delete() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row != null) workspace.delete("Заявка №" + row.request().id(), () -> workspace.requests.delete(row.request().id()));
    }

    @FXML private void export() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт текущего списка заявок");
        chooser.setInitialFileName("repairly-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV, UTF-8 (*.csv)", "*.csv"));
        File selected = chooser.showSaveDialog(workspace.owner);
        if (selected == null) return;
        var path = selected.toPath();
        boolean overwrite = Files.exists(path);
        if (overwrite && !Ui.confirm(workspace.owner, "Заменить существующий файл?", path.getFileName().toString())) return;
        var snapshot = List.copyOf(table.getItems());
        workspace.busy.set(true);
        workspace.tasks.run(() -> workspace.export.export(path, snapshot, overwrite), saved -> {
            workspace.busy.set(false);
            Ui.alert(workspace.owner, Alert.AlertType.INFORMATION, "Экспорт завершён · строк: " + snapshot.size(), saved.toString());
        }, error -> { workspace.busy.set(false); Ui.error(workspace.owner, error); });
    }
}
