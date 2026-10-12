package ru.rtumirea.repairly.fx.controller;

import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import ru.rtumirea.repairly.fx.*;
import ru.rtumirea.repairly.model.*;

import java.util.Locale;

public final class StatisticsController {
    @FXML private Label total, active, completed, completionRate, clients, technicians, completedCost, averageCost;
    @FXML private BarChart<Number, String> statusChart;
    @FXML private NumberAxis countAxis;
    private Workspace workspace;

    public void configure(Workspace workspace) {
        this.workspace = workspace;
        workspace.revision.addListener((obs, old, value) -> update());
        update();
    }

    private void update() {
        var stats = workspace.statistics.collect(workspace.requestRows.stream().map(RequestView::request).toList(),
                workspace.clientRows.size(), workspace.technicianRows.size());
        long done = stats.byStatus().get(RepairStatus.COMPLETED);
        long open = stats.requests() - done - stats.byStatus().get(RepairStatus.CANCELLED);
        total.setText(Integer.toString(stats.requests()));
        active.setText(Long.toString(open));
        completed.setText(Long.toString(done));
        completionRate.setText(String.format(Locale.forLanguageTag("ru-RU"), "%.1f %%", stats.requests() == 0 ? 0.0 : 100.0 * done / stats.requests()));
        clients.setText(Integer.toString(stats.clients()));
        technicians.setText(Integer.toString(stats.technicians()));
        completedCost.setText(Ui.money(stats.completedCost()));
        averageCost.setText(Ui.money(stats.averageCompletedCost()));
        completedCost.setTooltip(new javafx.scene.control.Tooltip(completedCost.getText()));
        averageCost.setTooltip(new javafx.scene.control.Tooltip(averageCost.getText()));
        long maximum = Math.max(1, stats.byStatus().values().stream().mapToLong(Long::longValue).max().orElse(1));
        double step = Math.max(1, Math.ceil(maximum / 5.0));
        countAxis.setAutoRanging(false);
        countAxis.setLowerBound(0);
        countAxis.setUpperBound(Math.ceil(maximum / step) * step);
        countAxis.setTickUnit(step);
        countAxis.setTickLabelFormatter(Ui.converter(n -> Long.toString(n.longValue())));
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (var status : RepairStatus.values()) series.getData().add(new XYChart.Data<>(stats.byStatus().get(status), status.label()));
        statusChart.getData().setAll(java.util.List.of(series));
    }
}
