package com.demo;

import com.demo.nosql.NoSqlDemo;
import com.demo.sql.SqlDemo;
import com.demo.util.Printer;

public class Main {

    public static void main(String[] args) {
        Printer.header("SQL vs NoSQL");
        System.out.println("  Use Case: Buchhandlung mit Büchern, Usern und Reviews");
        System.out.println("  SQL:   PostgreSQL (Neon)   — relationales Modell");
        System.out.println("  NoSQL: MongoDB (Atlas)     — dokumentenbasiertes Modell");

        // ── SQL Demo ──────────────────────────────────────────────────────────
        try (SqlDemo sql = new SqlDemo()) {
            sql.run();
        } catch (Exception e) {
            Printer.error("SQL Demo fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
        }

        // ── NoSQL Demo ────────────────────────────────────────────────────────
        try (NoSqlDemo nosql = new NoSqlDemo()) {
            nosql.run();
        } catch (Exception e) {
            Printer.error("NoSQL Demo fehlgeschlagen: " + e.getMessage());
            e.printStackTrace();
        }

    }
}
