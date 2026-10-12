package ru.rtumirea.repairly.fx;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.stage.Window;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.repository.*;
import ru.rtumirea.repairly.service.*;
import ru.rtumirea.repairly.util.ConnectionProvider;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shared observable state. Controllers use services, never SQL or JDBC connections. */
public final class Workspace {
    public final ClientService clients;
    public final TechnicianService technicians;
    public final RepairRequestService requests;
    public final StatisticsService statistics;
    public final CsvExportService export = new CsvExportService();
    public final FxTasks tasks;
    public final Window owner;
    public final ObservableList<Client> clientRows = FXCollections.observableArrayList();
    public final ObservableList<Technician> technicianRows = FXCollections.observableArrayList();
    public final ObservableList<RequestView> requestRows = FXCollections.observableArrayList();
    public final BooleanProperty busy = new SimpleBooleanProperty(false);
    public final BooleanProperty loaded = new SimpleBooleanProperty(false);
    public final StringProperty status = new SimpleStringProperty("Подключение к базе данных…");
    public final IntegerProperty revision = new SimpleIntegerProperty();

    public Workspace(ConnectionProvider database, FxTasks tasks, Window owner) {
        this.tasks = tasks;
        this.owner = owner;
        clients = new ClientService(new ClientRepository(database));
        technicians = new TechnicianService(new TechnicianRepository(database));
        requests = new RepairRequestService(new RepairRequestRepository(database), clients, technicians);
        statistics = new StatisticsService(clients, technicians, requests);
    }

    public void refresh() {
        if (busy.get()) return;
        busy.set(true);
        status.set("Обновляем данные…");
        tasks.run(() -> {
            var repairs = requests.findAll();
            var allClients = clients.findAll();
            var allTechnicians = technicians.findAll();
            Map<Integer, Client> c = allClients.stream().collect(Collectors.toMap(Client::id, Function.identity()));
            Map<Integer, Technician> t = allTechnicians.stream().collect(Collectors.toMap(Technician::id, Function.identity()));
            List<RequestView> rows = repairs.stream().map(r -> {
                Client client = c.get(r.clientId());
                if (client == null || (r.technicianId() != null && !t.containsKey(r.technicianId()))) {
                    throw new ru.rtumirea.repairly.exception.RepairlyException("Связанные данные изменились. Повторите обновление.");
                }
                return new RequestView(r, client, t.get(r.technicianId()));
            }).toList();
            return new Snapshot(allClients, allTechnicians, rows);
        }, snapshot -> {
            clientRows.setAll(snapshot.clients());
            technicianRows.setAll(snapshot.technicians());
            requestRows.setAll(snapshot.requests());
            loaded.set(true);
            revision.set(revision.get() + 1);
            status.set("Данные обновлены в " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            busy.set(false);
        }, error -> {
            busy.set(false);
            status.set(loaded.get() ? "Нет связи с БД · показаны последние загруженные данные" : "Нет связи с БД · нажмите «Обновить» после запуска Docker");
            Ui.error(owner, error);
        });
    }

    public void delete(String label, Runnable operation) {
        if (!Ui.confirm(owner, "Удалить «" + label + "»?", "Запись будет удалена из базы. Это действие нельзя отменить.")) return;
        busy.set(true);
        tasks.run(() -> { operation.run(); return true; }, ignored -> {
            busy.set(false);
            refresh();
        }, error -> { busy.set(false); Ui.error(owner, error); });
    }

    private record Snapshot(List<Client> clients, List<Technician> technicians, List<RequestView> requests) {}
}
