package ru.rtumirea.repairly.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ru.rtumirea.repairly.fx.Workspace;

public final class PersonDialogController {
    @FXML private Parent formRoot;
    @FXML private Label title, detailLabel, error;
    @FXML private TextField name, phone, detail;
    private Workspace workspace;
    private DirectoryController.Kind kind;
    private Integer id;
    private FormSupport form;

    public void configure(Workspace workspace, Stage stage, DirectoryController.Kind kind, DirectoryController.PersonRow row) {
        this.workspace = workspace;
        this.kind = kind;
        id = row == null ? null : row.id();
        form = new FormSupport(workspace, stage, formRoot, error);
        boolean client = kind == DirectoryController.Kind.CLIENT;
        title.setText(row == null ? (client ? "Новый клиент" : "Новый мастер") : (client ? "Редактирование клиента" : "Редактирование мастера"));
        detailLabel.setText(client ? "Адрес *" : "Специализация *");
        detail.setPromptText(client ? "Город, улица, дом и квартира" : "Например, холодильники и морозильники");
        if (row != null) {
            name.setText(row.name()); phone.setText(row.phone()); detail.setText(row.detail());
        }
    }

    @FXML private void save() {
        String n = name.getText(), p = phone.getText(), d = detail.getText();
        form.submit(() -> {
            if (kind == DirectoryController.Kind.CLIENT) {
                if (id == null) workspace.clients.create(n, p, d);
                else workspace.clients.update(id, n, p, d);
            } else {
                if (id == null) workspace.technicians.create(n, p, d);
                else workspace.technicians.update(id, n, p, d);
            }
        });
    }

    @FXML private void cancel() { form.cancel(); }
}
