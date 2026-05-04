package com.demo.nosql;

import com.demo.config.DatabaseConfig;
import com.demo.util.Printer;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.Arrays;
import java.util.List;

/**
 * Zeigt dokumentenbasierte Konzepte: eingebettete Dokumente, flexible Schemas,
 * keine JOINs, Schema-Evolution ohne Migration.
 *
 * Datenmodell (Buchhandlung):
 *   Collection "books" — jedes Dokument enthält Buch + Reviews eingebettet:
 *   {
 *     title: "...", author: "...",
 *     reviews: [ { userName: "...", rating: 5, text: "..." }, ... ]
 *   }
 */
public class NoSqlDemo implements AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> books;

    public NoSqlDemo() {
        this.client = DatabaseConfig.getMongoClient();
        MongoDatabase db = client.getDatabase(DatabaseConfig.getMongoDatabaseName());
        this.books = db.getCollection("books");
    }

    public void run() {
        Printer.section("NoSQL DEMO — MongoDB Atlas");
        prepareCollection();
        insertDocuments();
        queryAllBooks();
        queryEmbeddedReviews();
        demonstrateSchemaFlexibility();
        demonstrateAggregation();
        demonstrateFilterOnEmbedded();
        // cleanup() — auskommentiert damit Daten in Atlas sichtbar bleiben
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void prepareCollection() {
        Printer.step("Schritt 1: Collection vorbereiten");
        books.drop();
        Printer.nosql("db.books.drop()  // Collection leeren");
        Printer.info("Keine CREATE TABLE nötig — Collections existieren implizit.");
        Printer.info("Kein Schema definiert — Struktur ergibt sich aus den Dokumenten.");
    }

    // ─── Insert ───────────────────────────────────────────────────────────────

    private void insertDocuments() {
        Printer.step("Schritt 2: Dokumente einfügen (Buch + Reviews IN einem Dokument)");

        Document cleanCode = new Document("title", "Clean Code")
            .append("author", "Robert C. Martin")
            .append("isbn", "978-0132350884")
            .append("genre", "Programmierung")
            .append("reviews", Arrays.asList(
                new Document("userName", "Alice Müller")
                    .append("rating", 5)
                    .append("text", "Pflichtlektüre für jeden Entwickler!"),
                new Document("userName", "Bob Schmidt")
                    .append("rating", 4)
                    .append("text", "Sehr gut, aber etwas veraltet.")
            ));

        Document pragmaticProg = new Document("title", "The Pragmatic Programmer")
            .append("author", "David Thomas")
            .append("isbn", "978-0135957059")
            .append("genre", "Programmierung")
            .append("reviews", List.of(
                new Document("userName", "Alice Müller")
                    .append("rating", 5)
                    .append("text", "Absolut zeitlos – klare Empfehlung!")
            ));

        Document designPatterns = new Document("title", "Design Patterns")
            .append("author", "Gang of Four")
            .append("isbn", "978-0201633610")
            .append("genre", "Softwarearchitektur")
            .append("reviews", List.of(
                new Document("userName", "Bob Schmidt")
                    .append("rating", 3)
                    .append("text", "Gut für Fortgeschrittene, nicht für Anfänger.")
            ));

        books.insertMany(Arrays.asList(cleanCode, pragmaticProg, designPatterns));

        Printer.nosql("db.books.insertMany([{ title, author, reviews: [...] }, ...])");
        Printer.info("→ 3 Dokumente eingefügt. Reviews sind EINGEBETTET im Buch-Dokument.");
        Printer.info("  Kein separater 'users'-Eintrag nötig — Name direkt im Review gespeichert.");
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    private void queryAllBooks() {
        Printer.step("Schritt 3: Einfache Abfrage – alle Bücher");

        String query = "db.books.find({}, { title: 1, author: 1, genre: 1 })";
        Printer.nosql(query);

        Printer.result(String.format("%-35s %-25s %s", "Titel", "Autor", "Genre"));
        Printer.result("─".repeat(75));
        books.find()
            .projection(new Document("title", 1).append("author", 1).append("genre", 1).append("_id", 0))
            .forEach(doc -> Printer.result(String.format("%-35s %-25s %s",
                doc.getString("title"), doc.getString("author"), doc.getString("genre"))));
    }

    private void queryEmbeddedReviews() {
        Printer.step("Schritt 4: Bücher MIT Reviews — kein JOIN nötig!");
        Printer.info("Reviews sind eingebettet → alles in EINER Abfrage, EINER Collection.");

        Printer.nosql("db.books.find()  // Reviews automatisch enthalten");

        books.find().forEach(doc -> {
            System.out.println();
            Printer.result("📖  " + doc.getString("title") + " — " + doc.getString("author"));
            List<Document> reviews = doc.getList("reviews", Document.class);
            if (reviews != null) {
                reviews.forEach(r -> Printer.result(String.format("     → %d★  %-15s  \"%s\"",
                    r.getInteger("rating"), r.getString("userName"), r.getString("text"))));
            }
        });
    }

    // ─── Schema-Flexibilität ──────────────────────────────────────────────────

    private void demonstrateSchemaFlexibility() {
        Printer.step("Schritt 5: Schema-Flexibilität — neue Felder ohne Migration");
        Printer.info("Neue Felder einfach hinzufügen — andere Dokumente bleiben unverändert.");
        Printer.info("Kein ALTER TABLE, kein Downtime, keine NULL-Spalten für alle anderen.");

        books.updateOne(
            Filters.eq("title", "Clean Code"),
            Updates.combine(
                Updates.set("pageCount", 431),
                Updates.set("tags", Arrays.asList("clean-code", "refactoring", "best-practices")),
                Updates.set("edition", 1)
            )
        );

        Printer.nosql("db.books.updateOne(");
        Printer.nosql("  { title: 'Clean Code' },");
        Printer.nosql("  { $set: { pageCount: 431, tags: ['clean-code', ...], edition: 1 } }");
        Printer.nosql(")");

        Document updated = books.find(Filters.eq("title", "Clean Code")).first();
        if (updated != null) {
            Printer.result("'Clean Code' jetzt mit: pageCount=" + updated.getInteger("pageCount")
                + ", tags=" + updated.getList("tags", String.class));
        }

        Document designPatterns = books.find(Filters.eq("title", "Design Patterns")).first();
        if (designPatterns != null) {
            Printer.result("'Design Patterns': pageCount=" + designPatterns.get("pageCount")
                + "  ← Feld fehlt einfach, kein NULL-Problem");
        }

        Printer.info("→ Jedes Dokument kann eine andere Struktur haben (polymorph).");
    }

    // ─── Aggregation ──────────────────────────────────────────────────────────

    private void demonstrateAggregation() {
        Printer.step("Schritt 6: Aggregation — Durchschnittsbewertung pro Buch");
        Printer.info("MongoDB Aggregation Pipeline statt SQL GROUP BY + JOIN.");

        Printer.nosql("db.books.aggregate([");
        Printer.nosql("  { $unwind: '$reviews' },");
        Printer.nosql("  { $group: { _id: '$title', avgRating: { $avg: '$reviews.rating' }, count: { $sum: 1 } } },");
        Printer.nosql("  { $sort: { avgRating: -1 } }");
        Printer.nosql("])");

        books.aggregate(Arrays.asList(
            Aggregates.unwind("$reviews"),
            Aggregates.group("$title",
                Accumulators.avg("avgRating", "$reviews.rating"),
                Accumulators.sum("count", 1)
            ),
            Aggregates.sort(new Document("avgRating", -1))
        )).forEach(doc -> {
            double avg = doc.getDouble("avgRating");
            Printer.result(String.format("%-35s  ∅ %.2f★  (%d Review(s))",
                doc.getString("_id"), avg, doc.getInteger("count")));
        });
    }

    // ─── Filter auf eingebetteten Feldern ─────────────────────────────────────

    private void demonstrateFilterOnEmbedded() {
        Printer.step("Schritt 7: Filter auf eingebettetem Array — Bücher mit 5★-Reviews");
        Printer.info("Direkte Abfrage auf Array-Elemente ohne JOIN.");

        Printer.nosql("db.books.find({ 'reviews.rating': 5 }, { title: 1, reviews: 1 })");

        books.find(Filters.eq("reviews.rating", 5))
            .projection(new Document("title", 1).append("_id", 0))
            .forEach(doc -> Printer.result("✓  " + doc.getString("title")));
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanup() {
        books.drop();
        Printer.info("Demo-Collection gelöscht.");
    }

    @Override
    public void close() {
        client.close();
    }
}
