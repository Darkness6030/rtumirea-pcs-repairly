package ru.rtumirea.repairly.fx.controller;

import javafx.beans.binding.Bindings;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.rtumirea.repairly.fx.*;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;

/** One shared directory view avoids duplicating client / technician CRUD controllers. */
public final class DirectoryController {
    public enum Kind { CLIENT, TECHNICIAN }
    public record PersonRow(int id, String name, String phone, String detail, long requests) {}
    @FXML private Label title, subtitle, count;
    @FXML private TextField search;
    @FXML private TableView<PersonRow> table;
    @FXML private Button editButton, deleteButton, relatedButton;
    private final ObservableList<PersonRow> rows = FXCollections.observableArrayList();
    private Workspace workspace;
    private Kind kind;
    private IntConsumer showRelated;

    public void configure(Workspace workspace, Kind kind, IntConsumer showRelated) {
        this.workspace = workspace;
        this.kind = kind;
        this.showRelated = showRelated;
        boolean client = kind == Kind.CLIENT;
        title.setText(client ? "Клиенты" : "Мастера");
        subtitle.setText(client ? "Контакты владельцев техники и история обращений" : "Команда сервиса и назначенные ремонты");
        table.getColumns().setAll(java.util.List.of(Ui.column("Имя", 240, PersonRow::name),
                Ui.column("Телефон", 170, PersonRow::phone),
                Ui.column(client ? "Адрес" : "Специализация", 340, PersonRow::detail),
                Ui.column("Заявок", 90, PersonRow::requests)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Записей нет. Добавьте первую запись или измените поиск."));
        FilteredList<PersonRow> filtered = new FilteredList<>(rows);
        SortedList<PersonRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        search.textProperty().addListener((obs, old, text) -> {
            String query = text.strip().toLowerCase(Locale.ROOT);
            filtered.setPredicate(row -> (row.name() + " " + row.phone() + " " + row.detail()).toLowerCase(Locale.ROOT).contains(query));
        });
        count.textProperty().bind(Bindings.size(filtered).asString("Найдено: %d"));
        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(noSelection);
        deleteButton.disableProperty().bind(noSelection);
        relatedButton.disableProperty().bind(noSelection);
        table.setRowFactory(ignored -> {
            TableRow<PersonRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) edit(); });
            return row;
        });
        workspace.revision.addListener((obs, old, value) -> reload());
        reload();
    }

    private void reload() {
        PersonRow selection = table.getSelectionModel().getSelectedItem();
        if (kind == Kind.CLIENT) {
            rows.setAll(workspace.clientRows.stream().map(c -> new PersonRow(c.id(), c.name(), c.phone(), c.address(),
                    workspace.requestRows.stream().filter(r -> r.request().clientId() == c.id()).count())).toList());
        } else {
            rows.setAll(workspace.technicianRows.stream().map(t -> new PersonRow(t.id(), t.name(), t.phone(), t.specialization(),
                    workspace.requestRows.stream().filter(r -> Objects.equals(r.request().technicianId(), t.id())).count())).toList());
        }
        if (selection != null) rows.stream().filter(r -> r.id() == selection.id()).findFirst().ifPresent(table.getSelectionModel()::select);
    }

    @FXML private void add() { open(null); }
    @FXML private void edit() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row != null) open(row);
    }

    private void open(PersonRow row) {
        var view = Ui.<PersonDialogController>load("person-dialog.fxml");
        var stage = Ui.dialog(workspace.owner, kind == Kind.CLIENT ? "Клиент" : "Мастер", view.root());
        view.controller().configure(workspace, stage, kind, row);
        stage.showAndWait();
    }

    @FXML private void delete() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        workspace.delete(row.name(), () -> {
            if (kind == Kind.CLIENT) workspace.clients.delete(row.id());
            else workspace.technicians.delete(row.id());
        });
    }

    @FXML private void related() {
        var row = table.getSelectionModel().getSelectedItem();
        if (row != null) showRelated.accept(row.id());
    }
}
