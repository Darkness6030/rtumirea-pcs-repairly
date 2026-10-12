package ru.rtumirea.repairly.fx.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ru.rtumirea.repairly.exception.ValidationException;
import ru.rtumirea.repairly.fx.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.service.Validation;

import java.util.Arrays;

public final class RequestDialogController {
    private static final Technician UNASSIGNED = new Technician(null, "Не назначен", "", "");
    @FXML private Parent formRoot;
    @FXML private Label title, subtitle, error;
    @FXML private ComboBox<Client> client;
    @FXML private ComboBox<Technician> technician;
    @FXML private ComboBox<RepairStatus> status;
    @FXML private TextField applianceType, brand, model, cost;
    @FXML private TextArea problem;
    private Workspace workspace;
    private RepairRequest original;
    private FormSupport form;

    public void configure(Workspace workspace, Stage stage, RequestView row) {
        this.workspace = workspace;
        this.original = row == null ? null : row.request();
        form = new FormSupport(workspace, stage, formRoot, error);
        client.setConverter(Ui.converter(c -> c.name() + " · " + c.phone()));
        client.setItems(FXCollections.observableArrayList(workspace.clientRows));
        technician.setConverter(Ui.converter(t -> t.id() == null ? t.name() : t.name() + " · " + t.specialization()));
        technician.getItems().add(UNASSIGNED);
        technician.getItems().addAll(workspace.technicianRows);
        technician.setValue(UNASSIGNED);
        status.setConverter(Ui.converter(RepairStatus::label));
        if (original == null) {
            title.setText("Новая заявка");
            subtitle.setText("Укажите клиента и технику. Дата приёма устанавливается автоматически.");
            status.getItems().setAll(RepairStatus.NEW);
            status.setValue(RepairStatus.NEW);
            status.setDisable(true);
            cost.setText("0,00");
        } else {
            title.setText("Заявка №" + original.id());
            subtitle.setText("Принята " + Ui.DATE.format(original.createdAt()) + " · клиент закреплён за заявкой");
            client.setValue(row.client());
            client.setDisable(true);
            technician.setValue(row.technician() == null ? UNASSIGNED : row.technician());
            status.getItems().setAll(Arrays.stream(RepairStatus.values())
                    .filter(s -> s == original.status() || original.status().canTransitionTo(s)).toList());
            status.setValue(original.status());
            applianceType.setText(original.applianceType());
            brand.setText(original.brand());
            model.setText(original.model());
            problem.setText(original.problem());
            cost.setText(original.cost().toPlainString().replace('.', ','));
        }
    }

    @FXML private void save() {
        try {
            if (client.getValue() == null) throw new ValidationException("Клиент: выберите запись из списка. Нового клиента можно добавить в разделе «Клиенты».");
            Technician selected = technician.getValue();
            RequestInput input = new RequestInput(client.getValue().id(), selected == null ? null : selected.id(),
                    applianceType.getText(), brand.getText(), model.getText(), problem.getText(),
                    Validation.parseCost(cost.getText().strip()), status.getValue());
            form.submit(() -> {
                if (original == null) workspace.requests.create(input);
                else workspace.requests.update(original.id(), original.version(), input);
            });
        } catch (RuntimeException failure) { form.fail(failure); }
    }

    @FXML private void cancel() { form.cancel(); }
}
