package ru.rtumirea.repairly.repository;
import ru.rtumirea.repairly.model.Technician;
import ru.rtumirea.repairly.util.ConnectionProvider;
import java.sql.*;
public class TechnicianRepository extends JdbcRepository<Technician> {
    public TechnicianRepository(ConnectionProvider database) { super(database, "technicians"); }
    public Technician save(Technician entity) {
        int id = insert("INSERT INTO technicians (name, phone, specialization) VALUES (?, ?, ?)",
                entity.name(), entity.phone(), entity.specialization());
        return new Technician(id, entity.name(), entity.phone(), entity.specialization());
    }
    public boolean update(Technician entity) {
        return execute("UPDATE technicians SET name = ?, phone = ?, specialization = ? WHERE id = ?",
                entity.name(), entity.phone(), entity.specialization(), entity.id()) > 0;
    }
    protected Technician mapRow(ResultSet row) throws SQLException {
        return new Technician(row.getInt("id"), row.getString("name"), row.getString("phone"), row.getString("specialization"));
    }
}
