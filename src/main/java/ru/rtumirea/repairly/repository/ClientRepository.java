package ru.rtumirea.repairly.repository;

import ru.rtumirea.repairly.model.Client;
import ru.rtumirea.repairly.util.ConnectionProvider;

import java.sql.*;

public class ClientRepository extends JdbcRepository<Client> {
    public ClientRepository(ConnectionProvider database) {
        super(database, "clients");
    }

    public Client save(Client entity) {
        int id =
                insert(
                        "INSERT INTO clients (name, phone, address) VALUES (?, ?, ?)",
                        entity.name(),
                        entity.phone(),
                        entity.address());
        return new Client(id, entity.name(), entity.phone(), entity.address());
    }

    public boolean update(Client entity) {
        return execute(
                        "UPDATE clients SET name = ?, phone = ?, address = ? WHERE id = ?",
                        entity.name(),
                        entity.phone(),
                        entity.address(),
                        entity.id())
                > 0;
    }

    public Client findByPhone(String phone) {
        var rows = query("SELECT * FROM clients WHERE phone = ?", phone);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    protected Client mapRow(ResultSet row) throws SQLException {
        return new Client(
                row.getInt("id"),
                row.getString("name"),
                row.getString("phone"),
                row.getString("address"));
    }
}
