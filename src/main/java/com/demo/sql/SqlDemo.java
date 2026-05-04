package com.demo.sql;

import com.demo.config.DatabaseConfig;
import com.demo.util.Printer;

import java.sql.*;

/**
 * Zeigt relationale Konzepte: normalisierte Tabellen, Fremdschlüssel, JOINs, Schema-Rigidität.
 *
 * Datenmodell (Buchhandlung):
 *   sql_users  ←── sql_reviews ──→  sql_books
 *
 * Jede Entität hat eine eigene Tabelle. Beziehungen über Fremdschlüssel.
 */
public class SqlDemo implements AutoCloseable {

    private final Connection conn;

    public SqlDemo() throws SQLException {
        this.conn = DatabaseConfig.getPostgresConnection();
    }

    public void run() throws SQLException {
        Printer.section("SQL DEMO — PostgreSQL (Neon)");
        createTables();
        insertData();
        queryAllBooks();
        queryWithJoins();
        demonstrateSchemaRigidity();
        demonstrateTransaction();
        //dropTables(); //— auskommentiert damit Daten in Neon sichtbar bleiben
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        Printer.step("Schritt 1: Normalisierte Tabellen erstellen");
        Printer.info("Jede Entität bekommt eine eigene Tabelle mit festem Schema.");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sql_users (
                    id    SERIAL PRIMARY KEY,
                    name  VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sql_books (
                    id     SERIAL PRIMARY KEY,
                    title  VARCHAR(200) NOT NULL,
                    author VARCHAR(100) NOT NULL,
                    isbn   VARCHAR(20),
                    genre  VARCHAR(50)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sql_reviews (
                    id          SERIAL PRIMARY KEY,
                    user_id     INT REFERENCES sql_users(id) ON DELETE CASCADE,
                    book_id     INT REFERENCES sql_books(id) ON DELETE CASCADE,
                    rating      INT CHECK (rating BETWEEN 1 AND 5),
                    review_text TEXT,
                    created_at  TIMESTAMP DEFAULT NOW()
                )
            """);
        }

        Printer.sql("CREATE TABLE sql_users  (id SERIAL PRIMARY KEY, name VARCHAR, email VARCHAR UNIQUE)");
        Printer.sql("CREATE TABLE sql_books  (id SERIAL PRIMARY KEY, title VARCHAR, author VARCHAR, ...)");
        Printer.sql("CREATE TABLE sql_reviews(id SERIAL PRIMARY KEY, user_id INT REFERENCES sql_users, book_id INT REFERENCES sql_books, ...)");
        Printer.info("→ Schema ist STARR: Datentypen und Spalten müssen vorab definiert werden.");
    }

    // ─── Insert ───────────────────────────────────────────────────────────────

    private void insertData() throws SQLException {
        Printer.step("Schritt 2: Daten einfügen (normalisiert in 3 Tabellen)");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM sql_reviews");
            stmt.executeUpdate("DELETE FROM sql_books");
            stmt.executeUpdate("DELETE FROM sql_users");
        }

        int aliceId = insertUser("Alice Müller", "alice@example.com");
        int bobId   = insertUser("Bob Schmidt",  "bob@example.com");
        int cleanCodeId     = insertBook("Clean Code",              "Robert C. Martin", "978-0132350884", "Programmierung");
        int pragmaticProgId = insertBook("The Pragmatic Programmer","David Thomas",     "978-0135957059", "Programmierung");
        int designPatternsId= insertBook("Design Patterns",         "Gang of Four",     "978-0201633610", "Softwarearchitektur");

        insertReview(aliceId, cleanCodeId,      5, "Pflichtlektüre für jeden Entwickler!");
        insertReview(bobId,   cleanCodeId,      4, "Sehr gut, aber etwas veraltet.");
        insertReview(aliceId, pragmaticProgId,  5, "Absolut zeitlos – klare Empfehlung!");
        insertReview(bobId,   designPatternsId, 3, "Gut für Fortgeschrittene, nicht für Anfänger.");

        Printer.info("→ User und Reviews sind GETRENNT gespeichert (Normalisierung).");
        Printer.info("  User 'Alice' existiert einmal – nicht doppelt pro Review.");
    }

    private int insertUser(String name, String email) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sql_users (name, email) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, name);
            ps.setString(2, email);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private int insertBook(String title, String author, String isbn, String genre) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sql_books (title, author, isbn, genre) VALUES (?, ?, ?, ?) RETURNING id")) {
            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn);
            ps.setString(4, genre);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertReview(int userId, int bookId, int rating, String text) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sql_reviews (user_id, book_id, rating, review_text) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, bookId);
            ps.setInt(3, rating);
            ps.setString(4, text);
            ps.executeUpdate();
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    private void queryAllBooks() throws SQLException {
        Printer.step("Schritt 3: Einfache Abfrage – alle Bücher");

        String sql = "SELECT id, title, author, genre FROM sql_books ORDER BY title";
        Printer.sql(sql);

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Printer.result(String.format("%-5s %-35s %-25s %s", "ID", "Titel", "Autor", "Genre"));
            Printer.result("─".repeat(75));
            while (rs.next()) {
                Printer.result(String.format("%-5d %-35s %-25s %s",
                    rs.getInt("id"), rs.getString("title"),
                    rs.getString("author"), rs.getString("genre")));
            }
        }
    }

    private void queryWithJoins() throws SQLException {
        Printer.step("Schritt 4: JOIN-Abfrage – Bücher mit Reviews und Reviewer-Name");
        Printer.info("Um Buch + Review + Username zu verbinden: 2 JOINs notwendig.");

        String sql = """
            SELECT
                b.title       AS buch,
                u.name        AS reviewer,
                r.rating      AS sterne,
                r.review_text AS kommentar
            FROM sql_books b
            JOIN sql_reviews r ON b.id = r.book_id
            JOIN sql_users  u ON r.user_id = u.id
            ORDER BY b.title, r.rating DESC
        """;
        Printer.sql("SELECT b.title, u.name, r.rating, r.review_text");
        Printer.sql("FROM sql_books b JOIN sql_reviews r ON b.id = r.book_id");
        Printer.sql("               JOIN sql_users  u ON r.user_id = u.id");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Printer.result(String.format("%-35s %-16s %-7s %s", "Buch", "Reviewer", "Sterne", "Kommentar"));
            Printer.result("─".repeat(90));
            while (rs.next()) {
                Printer.result(String.format("%-35s %-16s  %d★    %s",
                    rs.getString("buch"), rs.getString("reviewer"),
                    rs.getInt("sterne"), rs.getString("kommentar")));
            }
        }

        // Aggregation
        Printer.step("Schritt 4b: Aggregation – Durchschnittsbewertung pro Buch");
        String aggSql = """
            SELECT b.title, ROUND(AVG(r.rating), 2) AS avg_rating, COUNT(*) AS anzahl_reviews
            FROM sql_books b
            JOIN sql_reviews r ON b.id = r.book_id
            GROUP BY b.title
            ORDER BY avg_rating DESC
        """;
        Printer.sql("SELECT b.title, AVG(r.rating), COUNT(*) FROM sql_books b JOIN sql_reviews r ON b.id = r.book_id GROUP BY b.title");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(aggSql)) {
            Printer.result(String.format("%-35s %-15s %s", "Buch", "∅ Bewertung", "Anzahl Reviews"));
            Printer.result("─".repeat(65));
            while (rs.next()) {
                Printer.result(String.format("%-35s %-15s %d",
                    rs.getString("title"), rs.getDouble("avg_rating"), rs.getInt("anzahl_reviews")));
            }
        }
    }

    // ─── Schema-Rigidität ─────────────────────────────────────────────────────

    private void demonstrateSchemaRigidity() throws SQLException {
        Printer.step("Schritt 5: Schema-Änderung — neue Spalte 'page_count' hinzufügen");
        Printer.info("In SQL: ALTER TABLE nötig. Bei Millionen Zeilen kann das Minuten dauern!");

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE sql_books ADD COLUMN IF NOT EXISTS page_count INT");
            stmt.executeUpdate("UPDATE sql_books SET page_count = 431 WHERE title = 'Clean Code'");
            stmt.executeUpdate("UPDATE sql_books SET page_count = 352 WHERE title = 'The Pragmatic Programmer'");
        }

        Printer.sql("ALTER TABLE sql_books ADD COLUMN page_count INT;");
        Printer.sql("UPDATE sql_books SET page_count = 431 WHERE title = 'Clean Code';");
        Printer.info("→ Spalte existiert jetzt für ALLE Zeilen. Bücher ohne Wert → NULL.");
        Printer.info("→ Alte App-Versionen die diese Spalte nicht kennen können brechen.");
    }

    // ─── Transaktionen ────────────────────────────────────────────────────────

    private void demonstrateTransaction() throws SQLException {
        Printer.step("Schritt 6: ACID-Transaktion — atomares Review-Löschen");
        Printer.info("SQL garantiert: Entweder ALLE Änderungen oder KEINE (Atomarität).");

        conn.setAutoCommit(false);
        try {
            Printer.sql("BEGIN;");
            Printer.sql("DELETE FROM sql_reviews WHERE rating < 3;  -- Schlechte Reviews löschen");
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate("DELETE FROM sql_reviews WHERE rating < 3");
                Printer.info("Gelöscht: " + deleted + " Review(s) mit Rating < 3");
            }
            Printer.sql("ROLLBACK;  -- Rückgängig machen (nur Demo)");
            conn.rollback();
            Printer.info("→ Rollback: Alle Daten wiederhergestellt. ACID in Aktion!");
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private void dropTables() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS sql_reviews");
            stmt.executeUpdate("DROP TABLE IF EXISTS sql_books");
            stmt.executeUpdate("DROP TABLE IF EXISTS sql_users");
        }
        Printer.info("Demo-Tabellen gelöscht.");
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
