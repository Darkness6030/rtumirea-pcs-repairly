package ru.rtumirea.repairly;

import org.junit.jupiter.api.Test;
import ru.rtumirea.repairly.exception.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.service.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class Kr2ServiceTest extends DatabaseTestSupport {
    private RequestInput input(Integer technicianId, RepairStatus status) {
        return new RequestInput(client.id(), technicianId, "Холодильник", "Atlant", "XM", "Не охлаждает\nМигает индикатор", new BigDecimal("1250.50"), status);
    }

    @Test void formCreateAndUpdateAreAtomicAndKeepBusinessRules() {
        var created = requests.create(input(technician.id(), RepairStatus.NEW));
        assertEquals(new BigDecimal("1250.50"), created.cost());
        assertEquals(technician.id(), created.technicianId());
        assertEquals("Не охлаждает\nМигает индикатор", created.problem());
        assertThrows(ValidationException.class, () -> requests.update(created.id(), 0, input(technician.id(), RepairStatus.COMPLETED)));
        assertEquals(created, requests.findById(created.id()));
        assertThrows(ValidationException.class, () -> requests.update(created.id(), 0, input(null, RepairStatus.DIAGNOSING)));
        assertEquals(created, requests.findById(created.id()));
        requests.update(created.id(), 0, input(technician.id(), RepairStatus.DIAGNOSING));
        var diagnosing = requests.findById(created.id());
        assertEquals(1, diagnosing.version());
        requests.update(created.id(), 1, input(technician.id(), RepairStatus.IN_REPAIR));
        requests.update(created.id(), 2, input(technician.id(), RepairStatus.COMPLETED));
        var completed = requests.findById(created.id());
        assertNotNull(completed.completedAt());
        assertFalse(completed.completedAt().isBefore(completed.createdAt()));
        assertThrows(ValidationException.class, () -> requests.update(created.id(), 3, input(technician.id(), RepairStatus.COMPLETED)));
    }

    @Test void staleDialogCannotOverwriteLatestChanges() {
        var created = requests.create(input(null, RepairStatus.NEW));
        requests.assignTechnician(created.id(), technician.id());
        assertThrows(ValidationException.class, () -> requests.update(created.id(), created.version(), input(null, RepairStatus.CANCELLED)));
        var current = requests.findById(created.id());
        assertEquals(technician.id(), current.technicianId());
        assertEquals(RepairStatus.NEW, current.status());
    }

    @Test void invalidFormDoesNotInsertAndClientCannotBeChanged() {
        assertThrows(ValidationException.class, () -> requests.create(input(technician.id(), RepairStatus.IN_REPAIR)));
        assertThrows(EntityNotFoundException.class, () -> requests.create(input(Integer.MAX_VALUE, RepairStatus.NEW)));
        var bad = new RequestInput(client.id(), null, "", "A", "B", "C", BigDecimal.ZERO, RepairStatus.NEW);
        assertThrows(ValidationException.class, () -> requests.create(bad));
        assertTrue(requests.findAll().isEmpty());
        var created = requests.create(input(null, RepairStatus.NEW));
        var second = clients.create("Другой клиент", "+70000000002", "Адрес");
        var changedClient = new RequestInput(second.id(), null, "A", "B", "C", "D", BigDecimal.ZERO, RepairStatus.NEW);
        assertThrows(ValidationException.class, () -> requests.update(created.id(), 0, changedClient));
        assertEquals(created, requests.findById(created.id()));
    }

    @Test void filterCombinesSearchStatusTechnicianAndInclusiveDates() {
        var a = requests.create(input(technician.id(), RepairStatus.NEW));
        var b = requests.create(input(null, RepairStatus.NEW));
        var row = new RequestView(a, client, technician);
        LocalDate day = a.createdAt().toLocalDate();
        for (String query : List.of("ATLANT", "холодильник", client.name(), client.phone(), technician.name(), "индикатор", a.id().toString())) {
            assertTrue(new RequestFilter(query, RepairStatus.NEW, technician.id(), day, day).test(row), query);
        }
        assertFalse(new RequestFilter("Atlant", RepairStatus.IN_REPAIR, null, null, null).test(row));
        assertFalse(new RequestFilter("", null, 0, null, null).test(row));
        assertTrue(new RequestFilter("", null, 0, null, null).test(new RequestView(b, client, null)));
        assertFalse(new RequestFilter("", null, null, day.plusDays(1), null).test(row));
        assertFalse(new RequestFilter("", null, null, null, day.minusDays(1)).test(row));
        assertThrows(ValidationException.class, () -> new RequestFilter("", null, null, day, day.minusDays(1)));
    }

    @Test void csvExportsOnlyPassedRowsInTheirOrderAndEscapesData() throws Exception {
        var a = requests.create(input(null, RepairStatus.NEW));
        var b = requests.create(new RequestInput(client.id(), null, "Печь", "=1+2", "A;\"B\"", "Строка 1\nСтрока 2", BigDecimal.TEN, RepairStatus.NEW));
        clients.update(client.id(), "=HYPERLINK(\"example\")", client.phone(), client.address());
        var currentClient = clients.findById(client.id());
        var exporter = new CsvExportService();
        var path = exporter.export(temp.resolve("selected.csv"), List.of(new RequestView(b, currentClient, null)), false);
        var text = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\ufeffНомер;Клиент;"));
        assertTrue(text.contains("\r\n\"" + b.id() + "\";"));
        assertFalse(text.contains("\r\n\"" + a.id() + "\";"));
        assertTrue(text.contains("\"'=1+2\""));
        assertTrue(text.contains("\"A;\"\"B\"\"\""));
        assertTrue(text.contains("\"Строка 1\nСтрока 2\""));
        assertTrue(text.contains("\"10,00\""));
        assertThrows(RepairlyException.class, () -> exporter.export(path, List.of(), false));
        assertEquals(text, Files.readString(path));
        exporter.export(path, List.of(new RequestView(b, client, null), new RequestView(a, client, null)), true);
        text = Files.readString(path);
        assertTrue(text.indexOf("\r\n\"" + b.id()) < text.indexOf("\r\n\"" + a.id()));
        exporter.export(path, List.of(), true);
        assertEquals(1, Files.readString(path).lines().count());
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
        assertThrows(ValidationException.class, () -> exporter.export(temp.resolve("invalid.txt"), List.of(), false));
    }

    @Test void statisticsHaveDefinedEmptyAndCompletedValues() {
        var service = new StatisticsService(clients, technicians, requests);
        var empty = service.collect(List.of(), 0, 0);
        assertEquals(0, empty.requests());
        assertEquals(new BigDecimal("0.00"), empty.averageCompletedCost());
        var now = LocalDateTime.now();
        var rows = List.of(new RepairRequest(1, client.id(), technician.id(), "A", "B", "C", "D", now, now, RepairStatus.COMPLETED, new BigDecimal("100.01"), 0),
                new RepairRequest(2, client.id(), technician.id(), "A", "B", "C", "D", now, now, RepairStatus.COMPLETED, new BigDecimal("200.00"), 0),
                new RepairRequest(3, client.id(), null, "A", "B", "C", "D", now, null, RepairStatus.CANCELLED, new BigDecimal("500.00"), 0));
        var stats = service.collect(rows, 1, 1);
        assertEquals(3, stats.requests());
        assertEquals(2L, stats.byStatus().get(RepairStatus.COMPLETED));
        assertEquals(new BigDecimal("300.01"), stats.completedCost());
        assertEquals(new BigDecimal("150.01"), stats.averageCompletedCost());
    }
}
