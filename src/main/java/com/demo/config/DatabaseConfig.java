package com.demo.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    public static Connection getPostgresConnection() throws SQLException {
        try {
            Driver driver = (Driver) Class.forName("org.postgresql.Driver")
                .getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(driver);
        } catch (Exception e) {
            throw new SQLException("PostgreSQL-Treiber konnte nicht geladen werden", e);
        }
        String url = "jdbc:postgresql://" + getEnv("POSTGRES_HOST") + "/" + getEnv("POSTGRES_DB");
        Properties props = new Properties();
        props.setProperty("user", getEnv("POSTGRES_USER"));
        props.setProperty("password", getEnv("POSTGRES_PASSWORD"));
        props.setProperty("sslmode", "require");
        return DriverManager.getConnection(url, props);
    }

    public static MongoClient getMongoClient() {
        String uri = getEnv("MONGODB_URI");
        return MongoClients.create(uri);
    }

    public static String getMongoDatabaseName() {
        return dotenv.get("MONGODB_DATABASE", "bookstore_demo");
    }

    private static String getEnv(String key) {
        String value = dotenv.get(key, System.getenv(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Umgebungsvariable '" + key + "' nicht gesetzt. Bitte .env Datei anlegen!"
            );
        }
        return value;
    }
}
