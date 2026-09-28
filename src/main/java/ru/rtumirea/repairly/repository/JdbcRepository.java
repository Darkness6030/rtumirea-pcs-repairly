package ru.rtumirea.repairly.repository;
import ru.rtumirea.repairly.exception.RepairlyException;
import ru.rtumirea.repairly.util.ConnectionProvider;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Table names are internal constants; all user values are bound parameters. */
public abstract class JdbcRepository<T> implements BaseRepository<T> {
    protected final ConnectionProvider database;
    private final String table;
    protected JdbcRepository(ConnectionProvider database, String table) {
        this.database = database; this.table = table;
    }
    protected abstract T mapRow(ResultSet row) throws SQLException;
    protected List<T> query(String sql, Object... params) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rows = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rows.next()) result.add(mapRow(rows));
                return result;
            }
        } catch (SQLException e) { throw databaseError(e); }
    }
    protected int insert(String sql, Object... params) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, params); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing generated ID");
                return keys.getInt(1);
            }
        } catch (SQLException e) { throw databaseError(e); }
    }
    protected int execute(String sql, Object... params) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params); return statement.executeUpdate();
        } catch (SQLException e) { throw databaseError(e); }
    }
    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
    }
    private static RepairlyException databaseError(SQLException e) {
        String message = switch (e.getSQLState() == null ? "" : e.getSQLState()) {
            case "23505" -> "Запись с таким телефоном уже существует.";
            case "23503" -> "Связанная запись отсутствует или используется заявкой. Сначала проверьте заявки.";
            case "23514", "23502", "22001", "22003" -> "Данные нарушают ограничения базы данных.";
            default -> "Ошибка работы с БД. Проверьте Docker, настройки .env и наличие таблиц.";
        };
        return new RepairlyException(message, e);
    }
    public List<T> findAll() { return query("SELECT * FROM " + table + " ORDER BY id"); }
    public T findById(int id) {
        List<T> rows = query("SELECT * FROM " + table + " WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public boolean deleteById(int id) { return execute("DELETE FROM " + table + " WHERE id = ?", id) > 0; }
}
