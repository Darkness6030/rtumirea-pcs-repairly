package ru.rtumirea.repairly.fx.controller;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import ru.rtumirea.repairly.fx.*;

/** Shared asynchronous save lifecycle for all forms. Failed saves keep the entered values. */
final class FormSupport {
    private final Workspace workspace;
    private final Stage stage;
    private final Parent root;
    private final Label error;
    private boolean saving;

    FormSupport(Workspace workspace, Stage stage, Parent root, Label error) {
        this.workspace = workspace;
        this.stage = stage;
        this.root = root;
        this.error = error;
        stage.setOnCloseRequest(event -> { if (saving) event.consume(); });
    }

    void submit(Runnable operation) {
        saving = true;
        root.setDisable(true);
        error.setText("Сохраняем…");
        workspace.tasks.run(() -> { operation.run(); return true; }, ignored -> {
            saving = false;
            stage.close();
            workspace.refresh();
        }, failure -> {
            saving = false;
            root.setDisable(false);
            fail(failure);
        });
    }

    void fail(Throwable failure) {
        error.setText(Ui.message(failure));
        org.slf4j.LoggerFactory.getLogger(FormSupport.class).warn("Form save failed", failure);
    }

    void cancel() { if (!saving) stage.close(); }
}
