package ru.rtumirea.repairly;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import ru.rtumirea.repairly.exception.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.repository.*;
import ru.rtumirea.repairly.service.*;
import ru.rtumirea.repairly.ui.InputReader;
import ru.rtumirea.repairly.util.ConnectionProvider;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Same suite runs on isolated H2 databases or temporary PostgreSQL schemas. */
class RepairlyTest extends DatabaseTestSupport {
    private RepairRequest create() {
        return requests.create(client.id(), "Стиральная машина", "Bosch", "WAN", "Не сливает воду");
    }

    private int inRepair(String cost) {
        int id = create().id();
        requests.assignTechnician(id, technician.id());
        requests.changeStatus(id, RepairStatus.DIAGNOSING);
        requests.updateDetails(
                id, "Стиральная машина", "Bosch", "WAN", "Не сливает воду", new BigDecimal(cost));
        requests.changeStatus(id, RepairStatus.IN_REPAIR);
        return id;
    }

    @Test
    void createsAndReadsRequest() {
        RepairRequest r = create();
        assertNotNull(r.id());
        assertEquals(RepairStatus.NEW, r.status());
        assertNull(r.technicianId());
        assertNull(r.completedAt());
        assertEquals(new BigDecimal("0.00"), r.cost());
        assertEquals(1, requests.findAll().size());
    }

    @Test
    void fullRepairLifecycle() {
        int id = inRepair("4500.50");
        requests.changeStatus(id, RepairStatus.WAITING_PARTS);
        requests.changeStatus(id, RepairStatus.IN_REPAIR);
        requests.changeStatus(id, RepairStatus.COMPLETED);
        RepairRequest r = requests.findById(id);
        assertEquals(RepairStatus.COMPLETED, r.status());
        assertEquals(new BigDecimal("4500.50"), r.cost());
        assertFalse(r.completedAt().isBefore(r.createdAt()));
    }

    @Test
    void diagnosisRequiresTechnician() {
        int id = create().id();
        assertThrows(
                ValidationException.class,
                () -> requests.changeStatus(id, RepairStatus.DIAGNOSING));
        assertEquals(RepairStatus.NEW, requests.findById(id).status());
    }

    @Test
    void rejectsSkippedStagesAndRepeatedStatus() {
        int id = create().id();
        requests.assignTechnician(id, technician.id());
        assertThrows(
                ValidationException.class, () -> requests.changeStatus(id, RepairStatus.COMPLETED));
        assertThrows(ValidationException.class, () -> requests.changeStatus(id, RepairStatus.NEW));
        assertThrows(ValidationException.class, () -> requests.changeStatus(id, null));
    }

    @Test
    void cancelledRequestIsImmutable() {
        int id = create().id();
        requests.changeStatus(id, RepairStatus.CANCELLED);
        assertNull(requests.findById(id).completedAt());
        assertThrows(
                ValidationException.class, () -> requests.assignTechnician(id, technician.id()));
        assertThrows(
                ValidationException.class,
                () -> requests.changeStatus(id, RepairStatus.DIAGNOSING));
        assertThrows(
                ValidationException.class,
                () -> requests.updateDetails(id, "A", "B", "C", "D", BigDecimal.TEN));
    }

    @Test
    void completedRequestIsImmutable() {
        int id = inRepair("0");
        requests.changeStatus(id, RepairStatus.COMPLETED);
        assertThrows(
                ValidationException.class, () -> requests.changeStatus(id, RepairStatus.CANCELLED));
        assertThrows(
                ValidationException.class,
                () -> requests.updateDetails(id, "A", "B", "C", "D", BigDecimal.TEN));
    }

    @Test
    void rejectsMissingReferences() {
        assertThrows(
                EntityNotFoundException.class, () -> requests.create(99999, "A", "B", "C", "D"));
        int id = create().id();
        assertThrows(EntityNotFoundException.class, () -> requests.assignTechnician(id, 99999));
        assertThrows(EntityNotFoundException.class, () -> requests.findById(99999));
    }

    @Test
    void rejectsBlankAndOversizedFields() {
        assertThrows(
                ValidationException.class, () -> requests.create(client.id(), " ", "B", "C", "D"));
        assertThrows(
                ValidationException.class,
                () -> requests.create(client.id(), "A", "B", "C", "x".repeat(2001)));
        assertThrows(
                ValidationException.class, () -> clients.create("", "+70000000009", "Address"));
        assertThrows(
                ValidationException.class, () -> technicians.create("Name", "+70000000019", ""));
    }

    @Test
    void validatesAndNormalizesPhones() {
        assertEquals("+70000000001", clients.findById(client.id()).phone());
        assertThrows(ValidationException.class, () -> clients.create("Name", "123", "Address"));
        assertThrows(
                RepairlyException.class, () -> clients.create("Other", "+70000000001", "Address"));
    }

    @Test
    void validatesMoneyWithoutRounding() {
        int id = create().id();
        for (String value : List.of("-0.01", "1.001", "10000000000")) {
            assertThrows(
                    ValidationException.class,
                    () -> requests.updateDetails(id, "A", "B", "C", "D", new BigDecimal(value)));
        }
        requests.updateDetails(id, "A", "B", "C", "D", new BigDecimal("1.10"));
        assertEquals(new BigDecimal("1.10"), requests.findById(id).cost());
    }

    @Test
    void preventsDeletingReferencedClientOrTechnician() {
        int id = create().id();
        requests.assignTechnician(id, technician.id());
        assertThrows(RepairlyException.class, () -> clients.delete(client.id()));
        assertThrows(RepairlyException.class, () -> technicians.delete(technician.id()));
        assertEquals(id, requests.findById(id).id());
    }

    @Test
    void deletesRecordsWhenUnreferenced() {
        int id = create().id();
        requests.delete(id);
        clients.delete(client.id());
        technicians.delete(technician.id());
        assertTrue(requests.findAll().isEmpty());
        assertTrue(clients.findAll().isEmpty());
        assertTrue(technicians.findAll().isEmpty());
        assertThrows(EntityNotFoundException.class, () -> requests.delete(id));
    }

    @Test
    void updatesClientsAndTechnicians() {
        clients.update(client.id(), "Новое имя", "+70000000002", "Новый адрес");
        technicians.update(technician.id(), "Другой мастер", "+70000000012", "Холодильники");
        assertEquals("Новое имя", clients.findById(client.id()).name());
        assertEquals("Холодильники", technicians.findById(technician.id()).specialization());
    }

    @Test
    void filtersAndSearchesCaseInsensitively() {
        int a = create().id();
        int b = create().id();
        requests.assignTechnician(a, technician.id());
        requests.changeStatus(b, RepairStatus.CANCELLED);
        assertEquals(1, requests.findByStatus(RepairStatus.NEW).size());
        assertEquals(1, requests.findByTechnicianId(technician.id()).size());
        assertEquals(2, requests.findByClientId(client.id()).size());
        assertEquals(2, requests.search("СЛИВАЕТ").size());
        assertTrue(requests.search("' OR 1=1 --").isEmpty());
    }

    @Test
    void sortsDatesCostsAndStatuses() {
        int a = inRepair("500");
        int b = create().id();
        assertEquals(b, requests.sortedByDate().getFirst().id());
        assertEquals(b, requests.sortedByCost().getFirst().id());
        assertEquals(b, requests.sortedByStatus().getFirst().id());
        assertEquals(a, requests.sortedByCost().getLast().id());
    }

    @Test
    void statisticsIncludeOnlyCompletedCosts() {
        int a = inRepair("100.10");
        requests.changeStatus(a, RepairStatus.COMPLETED);
        int b = inRepair("200.20");
        requests.changeStatus(b, RepairStatus.COMPLETED);
        inRepair("999.00");
        var s = new StatisticsService(clients, technicians, requests).collect();
        assertEquals(3, s.requests());
        assertEquals(2L, s.byStatus().get(RepairStatus.COMPLETED));
        assertEquals(new BigDecimal("300.30"), s.completedCost());
        assertEquals(new BigDecimal("150.15"), s.averageCompletedCost());
    }

    @Test
    void emptyStatisticsHaveZeroAverage() {
        var s = new StatisticsService(clients, technicians, requests).collect();
        assertEquals(new BigDecimal("0.00"), s.averageCompletedCost());
        assertEquals(0, s.requests());
    }

    @Test
    void optimisticLockRejectsLostUpdate() {
        RepairRequest stale = create();
        requests.assignTechnician(stale.id(), technician.id());
        assertFalse(repository.update(stale));
        assertEquals(technician.id(), requests.findById(stale.id()).technicianId());
    }

    @Test
    void schemaRejectsInvalidCostAndCompletion() throws Exception {
        int id = create().id();
        try (Connection c = database.open();
                Statement s = c.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> s.executeUpdate("UPDATE repair_requests SET cost = -1 WHERE id = " + id));
            assertThrows(
                    SQLException.class,
                    () ->
                            s.executeUpdate(
                                    "UPDATE repair_requests SET status = 'COMPLETED' WHERE id = "
                                            + id));
        }
    }

    @Test
    void exportsReadableWorkbookAndDoesNotOverwrite() throws Exception {
        create();
        var exporter = new ExportService(clients, technicians, requests);
        Path path = exporter.exportToExcel(temp.resolve("report.xlsx"));
        try (var stream = Files.newInputStream(path);
                var book = new XSSFWorkbook(stream)) {
            assertEquals(3, book.getNumberOfSheets());
            assertEquals(1, book.getSheet("Заявки").getLastRowNum());
            assertEquals(
                    "Bosch", book.getSheet("Заявки").getRow(1).getCell(4).getStringCellValue());
            assertEquals(
                    "+70000000001",
                    book.getSheet("Клиенты").getRow(1).getCell(2).getStringCellValue());
        }
        byte[] original = Files.readAllBytes(path);
        assertThrows(RepairlyException.class, () -> exporter.exportToExcel(path));
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    @Test
    void spreadsheetTreatsUserInputAsText() throws Exception {
        clients.update(client.id(), "=1+1", client.phone(), client.address());
        Path path =
                new ExportService(clients, technicians, requests)
                        .exportToExcel(temp.resolve("text.xlsx"));
        try (var stream = Files.newInputStream(path);
                var book = new XSSFWorkbook(stream)) {
            assertEquals(
                    org.apache.poi.ss.usermodel.CellType.STRING,
                    book.getSheet("Клиенты").getRow(1).getCell(1).getCellType());
        }
    }

    @Test
    void inputRetriesBadNumbersAndAcceptsDecimalComma() {
        InputReader input = new InputReader(new Scanner("bad\n42\nwrong\n12,50\n"));
        assertEquals(42, input.integer(""));
        assertEquals(new BigDecimal("12.50"), input.money(""));
        assertThrows(NoSuchElementException.class, () -> input.text(""));
    }

    @Test
    void sqlPayloadsRemainLiteralDataForAllRepositories() {
        String[] payloads = {
            "O'Connor", "x'); DROP TABLE clients; --", "' OR 1=1 --", "\"; SELECT pg_sleep(5); --"
        };
        for (String payload : payloads) {
            clients.update(client.id(), payload, client.phone(), payload);
            technicians.update(technician.id(), payload, technician.phone(), payload);
            assertEquals(payload, clients.findById(client.id()).name());
            assertEquals(payload, clients.findById(client.id()).address());
            assertEquals(payload, technicians.findById(technician.id()).specialization());
            RepairRequest r = requests.create(client.id(), payload, payload, payload, payload);
            requests.assignTechnician(r.id(), technician.id());
            requests.updateDetails(r.id(), payload, payload, payload, payload, BigDecimal.TEN);
            RepairRequest saved = requests.findById(r.id());
            assertEquals(payload, saved.applianceType());
            assertEquals(payload, saved.brand());
            assertEquals(payload, saved.model());
            assertEquals(payload, saved.problem());
            assertTrue(requests.search(payload).stream().anyMatch(row -> row.id().equals(r.id())));
        }
        assertEquals(1, clients.findAll().size());
        assertEquals(1, technicians.findAll().size());
        assertEquals(payloads.length, requests.findAll().size());
        Client inserted = clients.create(payloads[1], "+70000000002", payloads[1]);
        Technician worker = technicians.create(payloads[2], "+70000000012", payloads[2]);
        assertEquals(payloads[1], clients.findById(inserted.id()).name());
        assertEquals(payloads[2], technicians.findById(worker.id()).name());
    }

    @Test
    void productionRepositoriesUsePreparedStatementsOnly() throws Exception {
        ConnectionProvider guarded =
                () -> {
                    Connection real = database.open();
                    return (Connection)
                            java.lang.reflect.Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("createStatement")) {
                                            throw new AssertionError(
                                                    "Production code must use PreparedStatement");
                                        }
                                        try {
                                            return method.invoke(real, args);
                                        } catch (java.lang.reflect.InvocationTargetException e) {
                                            throw e.getCause();
                                        }
                                    });
                };
        ClientService c = new ClientService(new ClientRepository(guarded));
        TechnicianService t = new TechnicianService(new TechnicianRepository(guarded));
        RepairRequestService r =
                new RepairRequestService(new RepairRequestRepository(guarded), c, t);
        Client newClient = c.create("O'Connor", "+70000000002", "Address");
        Technician newTech = t.create("Master", "+70000000012", "Repair");
        c.update(newClient.id(), "Updated", newClient.phone(), "Address");
        t.update(newTech.id(), "Updated", newTech.phone(), "Repair");
        int id = r.create(newClient.id(), "A", "B", "C", "D").id();
        r.assignTechnician(id, newTech.id());
        r.changeStatus(id, RepairStatus.DIAGNOSING);
        r.updateDetails(id, "A", "B", "C", "D", BigDecimal.TEN);
        assertEquals(1, r.findByClientId(newClient.id()).size());
        assertEquals(1, r.findByTechnicianId(newTech.id()).size());
        assertEquals(1, r.findByStatus(RepairStatus.DIAGNOSING).size());
        assertEquals(1, r.findAll().size());
        assertEquals(2, c.findAll().size());
        assertEquals(2, t.findAll().size());
        r.delete(id);
        c.delete(newClient.id());
        t.delete(newTech.id());
    }

    @Test
    void rejectsNonPositiveIdsAndInvalidExportTargets() {
        for (int id : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(ValidationException.class, () -> clients.findById(id));
            assertThrows(ValidationException.class, () -> technicians.delete(id));
            assertThrows(ValidationException.class, () -> requests.findById(id));
            assertThrows(
                    ValidationException.class, () -> clients.update(id, "A", client.phone(), "B"));
            assertThrows(ValidationException.class, () -> requests.create(id, "A", "B", "C", "D"));
        }
        ExportService exporter = new ExportService(clients, technicians, requests);
        assertThrows(ValidationException.class, () -> exporter.exportToExcel(null));
        assertThrows(
                ValidationException.class,
                () -> exporter.exportToExcel(temp.resolve("report.txt")));
        assertThrows(ValidationException.class, () -> exporter.exportToExcel(Path.of("/")));
    }

    @Test
    void menuRecoversFromInvalidInputWithoutPartialWrites() {
        String script =
                "oops\n"
                    + "2147483648\n"
                    + "99\n"
                    + "5\n"
                    + "999\n"
                    + "1\n"
                    + "2\n"
                    + " \n"
                    + "+70000000009\n"
                    + "Address\n"
                    + "0\n"
                    + "4\n"
                    + "1\n"
                    + "0\n"
                    + "-2\n"
                    + "999999\n"
                    + "0\n"
                    + "8\n"
                    + "bad.txt\n"
                    + "8\n"
                    + "bad\u0000.xlsx\n"
                    + "7\n"
                    + "0\n";
        String output = runConsole(script, clients, technicians, requests);
        assertTrue(output.contains("Введите целое число"));
        assertTrue(output.contains("Выберите статус"));
        assertTrue(output.contains("ID должен быть положительным"));
        assertTrue(output.contains("Клиентов: 1"));
        assertTrue(output.contains("До свидания!"));
        assertEquals(1, clients.findAll().size());
        assertTrue(requests.findAll().isEmpty());
    }

    @Test
    void eofDuringCreationExitsWithoutSavingPartialEntity() {
        String output = runConsole("1\n2\nOnly name\n", clients, technicians, requests);
        assertTrue(output.contains("Ввод завершён"));
        assertEquals(1, clients.findAll().size());
    }

    @Test
    void unavailableDatabaseIsReportedAndMenuKeepsWorking() {
        ConnectionProvider unavailable =
                () -> {
                    throw new SQLException("Do not disclose raw connection detail", "08001");
                };
        ClientService c = new ClientService(new ClientRepository(unavailable));
        TechnicianService t = new TechnicianService(new TechnicianRepository(unavailable));
        RepairRequestService r =
                new RepairRequestService(new RepairRequestRepository(unavailable), c, t);
        String output = runConsole("1\n1\n0\n0\n", c, t, r);
        assertTrue(output.contains("Ошибка работы с БД"));
        assertTrue(output.contains("До свидания!"));
        assertFalse(output.contains("Do not disclose"));
    }

    @Test
    void exportIoFailureIsRecoverable() throws Exception {
        Path blocker = temp.resolve("existing-file");
        Files.writeString(blocker, "unchanged");
        assertThrows(
                RepairlyException.class,
                () ->
                        new ExportService(clients, technicians, requests)
                                .exportToExcel(blocker.resolve("report.xlsx")));
        assertEquals("unchanged", Files.readString(blocker));
    }

    private String runConsole(
            String script, ClientService c, TechnicianService t, RepairRequestService r) {
        java.io.PrintStream previous = System.out;
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.io.PrintStream output =
                new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(output);
            new ru.rtumirea.repairly.ui.ConsoleApplication(
                            new InputReader(new Scanner(script)), c, t, r)
                    .start();
        } finally {
            System.setOut(previous);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
