package ru.rtumirea.repairly.util;

import io.github.cdimascio.dotenv.Dotenv;

import ru.rtumirea.repairly.exception.ValidationException;

import java.sql.DriverManager;

public final class DatabaseManager {
    private DatabaseManager() {}

    public static ConnectionProvider fromEnvironment() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String url = env.get("DB_URL", "jdbc:postgresql://localhost:55432/repairly");
        String user = env.get("DB_USER", "repairly");
        String password = env.get("DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new ValidationException("Задайте DB_PASSWORD: скопируйте .env.example в .env.");
        }
        return () -> DriverManager.getConnection(url, user, password);
    }
}
