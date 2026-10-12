package ru.rtumirea.repairly.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import ru.rtumirea.repairly.fx.Workspace;

public final class MainController {
    @FXML private StackPane pages;
    @FXML private Node requestsPage, clientsPage, techniciansPage, statisticsPage;
    @FXML private RequestsController requestsPageController;
    @FXML private DirectoryController clientsPageController, techniciansPageController;
    @FXML private StatisticsController statisticsPageController;
    @FXML private ToggleButton requestsNav, clientsNav, techniciansNav, statisticsNav;
    @FXML private Label connectionStatus;
    @FXML private ProgressIndicator progress;
    @FXML private Button refresh;
    private Workspace workspace;

    public void configure(Workspace workspace) {
        this.workspace = workspace;
        requestsPageController.configure(workspace);
        clientsPageController.configure(workspace, DirectoryController.Kind.CLIENT, id -> {
            showRequests(); requestsPageController.showClient(id);
        });
        techniciansPageController.configure(workspace, DirectoryController.Kind.TECHNICIAN, id -> {
            showRequests(); requestsPageController.showTechnician(id);
        });
        statisticsPageController.configure(workspace);
        connectionStatus.textProperty().bind(workspace.status);
        progress.visibleProperty().bind(workspace.busy);
        refresh.disableProperty().bind(workspace.busy);
        pages.disableProperty().bind(workspace.busy.or(workspace.loaded.not()));
        showRequests();
    }

    @FXML private void refreshData() { workspace.refresh(); }
    @FXML private void showRequests() { show(requestsPage, requestsNav); }
    @FXML private void showClients() { show(clientsPage, clientsNav); }
    @FXML private void showTechnicians() { show(techniciansPage, techniciansNav); }
    @FXML private void showStatistics() { show(statisticsPage, statisticsNav); }

    private void show(Node selected, ToggleButton button) {
        for (Node page : pages.getChildren()) {
            page.setVisible(page == selected);
            page.setManaged(page == selected);
        }
        button.setSelected(true);
    }
}
