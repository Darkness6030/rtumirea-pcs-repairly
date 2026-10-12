package ru.rtumirea.repairly.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ru.rtumirea.repairly.fx.controller.MainController;
import ru.rtumirea.repairly.util.DatabaseManager;

public final class RepairlyApplication extends Application {
    private FxTasks tasks;

    @Override
    public void start(Stage stage) {
        tasks = new FxTasks();
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> Ui.error(stage, error));
        try {
            var view = Ui.<MainController>load("main.fxml");
            Scene scene = new Scene(view.root(), 1280, 820);
            Ui.style(scene);
            stage.setTitle("Repairly · Сервис ремонта");
            stage.setMinWidth(1050);
            stage.setMinHeight(700);
            stage.setScene(scene);
            stage.show();
            Platform.runLater(() -> {
                try {
                    var workspace = new Workspace(DatabaseManager.fromEnvironment(), tasks, stage);
                    view.controller().configure(workspace);
                    workspace.refresh();
                } catch (RuntimeException e) {
                    Ui.error(stage, e);
                    stage.close();
                }
            });
        } catch (RuntimeException e) {
            Ui.error(stage, e);
            Platform.exit();
        }
    }

    @Override public void stop() { if (tasks != null) tasks.close(); }
}
