package ru.rtumirea.repairly.repository;

import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.util.ConnectionProvider;

import java.sql.*;
import java.util.List;

public class RepairRequestRepository extends JdbcRepository<RepairRequest> {
    public RepairRequestRepository(ConnectionProvider database) {
        super(database, "repair_requests");
    }

    public RepairRequest save(RepairRequest r) {
        int id =
                insert(
                        """
INSERT INTO repair_requests (client_id, technician_id, appliance_type, brand, model,
    problem, created_at, completed_at, status, cost, version)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""",
                        r.clientId(),
                        r.technicianId(),
                        r.applianceType(),
                        r.brand(),
                        r.model(),
                        r.problem(),
                        r.createdAt(),
                        r.completedAt(),
                        r.status().name(),
                        r.cost(),
                        r.version());
        return findById(id);
    }

    /** Optimistic lock prevents two clients from silently overwriting each other. */
    public boolean update(RepairRequest r) {
        return execute(
                        """
UPDATE repair_requests SET technician_id = ?, appliance_type = ?, brand = ?, model = ?,
    problem = ?, completed_at = ?, status = ?, cost = ?, version = version + 1
WHERE id = ? AND version = ?
""",
                        r.technicianId(),
                        r.applianceType(),
                        r.brand(),
                        r.model(),
                        r.problem(),
                        r.completedAt(),
                        r.status().name(),
                        r.cost(),
                        r.id(),
                        r.version())
                > 0;
    }

    public List<RepairRequest> findByClientId(int id) {
        return query("SELECT * FROM repair_requests WHERE client_id = ? ORDER BY id", id);
    }

    public List<RepairRequest> findByTechnicianId(int id) {
        return query("SELECT * FROM repair_requests WHERE technician_id = ? ORDER BY id", id);
    }

    public List<RepairRequest> findByStatus(RepairStatus status) {
        return query("SELECT * FROM repair_requests WHERE status = ? ORDER BY id", status.name());
    }

    protected RepairRequest mapRow(ResultSet row) throws SQLException {
        Timestamp completed = row.getTimestamp("completed_at");
        return new RepairRequest(
                row.getInt("id"),
                row.getInt("client_id"),
                row.getObject("technician_id", Integer.class),
                row.getString("appliance_type"),
                row.getString("brand"),
                row.getString("model"),
                row.getString("problem"),
                row.getTimestamp("created_at").toLocalDateTime(),
                completed == null ? null : completed.toLocalDateTime(),
                RepairStatus.valueOf(row.getString("status")),
                row.getBigDecimal("cost"),
                row.getInt("version"));
    }
}
