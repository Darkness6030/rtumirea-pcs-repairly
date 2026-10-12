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

/** Isolated test schema for H2 or PostgreSQL; public data is never changed. */
abstract class DatabaseTestSupport {
    ConnectionProvider database;
    ClientService clients;
    TechnicianService technicians;
    RepairRequestRepository repository;
    RepairRequestService requests;
    Client client;
    Technician technician;
    String schema;
    String postgres;
    @TempDir Path temp;

    @BeforeEach
    void setup() throws Exception {
        postgres = System.getenv("TEST_DB_URL");
        schema = "repairly_test_" + UUID.randomUUID().toString().replace("-", "");
        if (postgres != null) {
            try (Connection c = postgresConnection();
                    Statement s = c.createStatement()) {
                s.execute("CREATE SCHEMA " + schema);
            }
            database =
                    () -> {
                        Connection c = postgresConnection();
                        c.setSchema(schema);
                        return c;
                    };
        } else {
            String url =
                    "jdbc:h2:mem:"
                            + schema
                            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
            database = () -> DriverManager.getConnection(url, "sa", "");
        }
        try (var stream = getClass().getResourceAsStream("/db/migration/initial.sql")) {
            assertNotNull(stream);
            String sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            try (Connection c = database.open();
                    Statement s = c.createStatement()) {
                for (String statement : sql.split(";")) {
                    if (!statement.isBlank()) {
                        s.execute(statement);
                    }
                }
            }
        }
        clients = new ClientService(new ClientRepository(database));
        technicians = new TechnicianService(new TechnicianRepository(database));
        repository = new RepairRequestRepository(database);
        requests = new RepairRequestService(repository, clients, technicians);
        client = clients.create("Тестовый клиент", "+7 (000) 000-00-01", "Учебный адрес");
        technician = technicians.create("Тестовый мастер", "+70000000011", "Стиральные машины");
    }

    private Connection postgresConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres, System.getenv("TEST_DB_USER"), System.getenv("TEST_DB_PASSWORD"));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (database == null) {
            return;
        }
        if (postgres != null) {
            try (Connection c = postgresConnection();
                    Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        } else {
            try (Connection c = database.open();
                    Statement s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }

}
