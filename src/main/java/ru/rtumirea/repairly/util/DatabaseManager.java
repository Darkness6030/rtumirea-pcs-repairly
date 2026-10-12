package ru.rtumirea.repairly.util;

import io.github.cdimascio.dotenv.Dotenv;

import ru.rtumirea.repairly.exception.ValidationException;

import java.sql.DriverManager;
import java.util.Properties;
import java.io.IOException;

public final class DatabaseManager {
    private DatabaseManager() {}

    public static ConnectionProvider fromEnvironment() {
        Dotenv env = Dotenv.configure().directory(System.getProperty("repairly.config", ".")).ignoreIfMissing().load();
        Properties defaults = new Properties();
        try (var input = DatabaseManager.class.getResourceAsStream("/database.properties")) {
            if (input == null) throw new IOException("Missing database.properties");
            defaults.load(input);
        } catch (IOException e) {
            throw new ru.rtumirea.repairly.exception.RepairlyException("Не удалось прочитать database.properties.", e);
        }
        String url = env.get("DB_URL", defaults.getProperty("db.url"));
        String user = env.get("DB_USER", defaults.getProperty("db.user"));
        String password = env.get("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ValidationException("Задайте DB_PASSWORD: скопируйте .env.example в .env.");
        }
        Properties connection = new Properties();
        connection.setProperty("user", user);
        connection.setProperty("password", password);
        connection.setProperty("connectTimeout", "5");
        connection.setProperty("socketTimeout", "15");
        connection.setProperty("ApplicationName", "Repairly JavaFX");
        return () -> DriverManager.getConnection(url, connection);
    }
}
