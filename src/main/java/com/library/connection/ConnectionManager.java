package com.library.connection;

import java.sql.*;

/**
 * ConnectionManager
 * -----------------
 * Handles Derby embedded database initialization,
 * connection lifecycle, and graceful shutdown.
 *
 * Phase 1 – Database Initialization & Schema Design
 */
public class ConnectionManager {

    // ---------------------------------------------------------------
    // TODO: Replace with your GitHub repository link below
    // GitHub Repo: https://github.com/<YOUR_USERNAME>/<YOUR_REPO>
    // ---------------------------------------------------------------

    private static final String DB_URL_CREATE  = "jdbc:derby:librarydb;create=true";
    private static final String DB_URL_CONNECT = "jdbc:derby:librarydb";
    private static final String DB_URL_SHUTDOWN = "jdbc:derby:librarydb;shutdown=true";
    private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

    private static ConnectionManager instance;
    private Connection connection;

    // ── Singleton ────────────────────────────────────────────────────
    private ConnectionManager() {}

    public static ConnectionManager getInstance() {
        if (instance == null) {
            instance = new ConnectionManager();
        }
        return instance;
    }

    // ── Connect / Reconnect ──────────────────────────────────────────
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName(DRIVER);
            } catch (ClassNotFoundException e) {
                throw new SQLException("Derby driver not found. Add derby.jar to classpath.", e);
            }
            connection = DriverManager.getConnection(DB_URL_CONNECT);
        }
        return connection;
    }

    // ── Initial Setup (called once on first run) ──────────────────────
    public void initializeDatabase() throws SQLException {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Derby driver not found.", e);
        }

        // Use create=true only during initialization
        try (Connection conn = DriverManager.getConnection(DB_URL_CREATE)) {
            createSchema(conn);
            createIndexes(conn);
            seedData(conn);
            System.out.println("[DB] Database initialized successfully.");
        }
    }

    // ── Schema Creation ───────────────────────────────────────────────
    private void createSchema(Connection conn) throws SQLException {
        String[] ddl = {
            // Members table
            "CREATE TABLE Members (" +
            "  MemberID    INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
            "  Name        VARCHAR(100) NOT NULL," +
            "  Email       VARCHAR(150) UNIQUE NOT NULL," +
            "  ActiveLoans INT DEFAULT 0 CHECK (ActiveLoans >= 0)" +
            ")",

            // Books table
            "CREATE TABLE Books (" +
            "  BookID      INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
            "  Title       VARCHAR(200) NOT NULL," +
            "  Author      VARCHAR(100) NOT NULL," +
            "  ISBN        VARCHAR(20)  UNIQUE NOT NULL," +
            "  Available   BOOLEAN DEFAULT TRUE" +
            ")",

            // Loans table
            "CREATE TABLE Loans (" +
            "  LoanID      INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," +
            "  MemberID    INT NOT NULL REFERENCES Members(MemberID)," +
            "  BookID      INT NOT NULL REFERENCES Books(BookID)," +
            "  LoanDate    DATE NOT NULL DEFAULT CURRENT_DATE," +
            "  DueDate     DATE NOT NULL," +
            "  ReturnDate  DATE" +
            ")"
        };

        DatabaseMetaData meta = conn.getMetaData();

        for (String sql : ddl) {
            // Extract table name from DDL
            String tableName = sql.split("TABLE ")[1].split(" ")[0].trim();
            try (ResultSet rs = meta.getTables(null, "APP", tableName.toUpperCase(), null)) {
                if (!rs.next()) {
                    try (Statement st = conn.createStatement()) {
                        st.execute(sql);
                        System.out.println("[DB] Table created: " + tableName);
                    }
                } else {
                    System.out.println("[DB] Table already exists: " + tableName);
                }
            }
        }
    }

    // ── Index Creation ────────────────────────────────────────────────
    private void createIndexes(Connection conn) throws SQLException {
        String[][] indexes = {
            {"idx_books_isbn",   "CREATE INDEX idx_books_isbn ON Books(ISBN)"},
            {"idx_loans_member", "CREATE INDEX idx_loans_member ON Loans(MemberID)"},
            {"idx_loans_return", "CREATE INDEX idx_loans_return ON Loans(ReturnDate)"},
            {"idx_loans_due",    "CREATE INDEX idx_loans_due ON Loans(DueDate)"}
        };

        DatabaseMetaData meta = conn.getMetaData();
        for (String[] idx : indexes) {
            try (ResultSet rs = meta.getIndexInfo(null, "APP", "LOANS", false, false)) {
                // Simple guard: just try; Derby ignores if index exists only via exception
            }
            try (Statement st = conn.createStatement()) {
                st.execute(idx[1]);
                System.out.println("[DB] Index created: " + idx[0]);
            } catch (SQLException e) {
                if (e.getSQLState().equals("X0Y32")) {
                    System.out.println("[DB] Index already exists: " + idx[0]);
                } else {
                    throw e;
                }
            }
        }
    }

    // ── Seed Data ─────────────────────────────────────────────────────
    private void seedData(Connection conn) throws SQLException {
        // Check if already seeded
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Members")) {
            rs.next();
            if (rs.getInt(1) > 0) {
                System.out.println("[DB] Seed data already present, skipping.");
                return;
            }
        }

        String insertMember = "INSERT INTO Members (Name, Email) VALUES (?, ?)";
        String insertBook   = "INSERT INTO Books (Title, Author, ISBN) VALUES (?, ?, ?)";

        try (PreparedStatement pm = conn.prepareStatement(insertMember);
             PreparedStatement pb = conn.prepareStatement(insertBook)) {

            // Members
            String[][] members = {
                {"Alice Kumar",   "alice@library.com"},
                {"Bob Sharma",    "bob@library.com"},
                {"Carol Singh",   "carol@library.com"},
                {"David Patel",   "david@library.com"},
                {"Eva Reddy",     "eva@library.com"}
            };
            for (String[] m : members) {
                pm.setString(1, m[0]);
                pm.setString(2, m[1]);
                pm.addBatch();
            }
            pm.executeBatch();

            // Books
            String[][] books = {
                {"Clean Code",            "Robert C. Martin", "978-0132350884"},
                {"Effective Java",        "Joshua Bloch",     "978-0134685991"},
                {"Design Patterns",       "Gang of Four",     "978-0201633610"},
                {"The Pragmatic Programmer","Hunt & Thomas",  "978-0201616224"},
                {"Introduction to Algorithms","CLRS",         "978-0262033848"},
                {"Database Systems",      "Ramakrishnan",     "978-0072465631"},
                {"Java Concurrency in Practice","Goetz",      "978-0321349606"},
                {"Head First Java",       "Kathy Sierra",     "978-0596009205"},
                {"Refactoring",           "Martin Fowler",    "978-0134757599"},
                {"You Don't Know JS",     "Kyle Simpson",     "978-1491904244"}
            };
            for (String[] b : books) {
                pb.setString(1, b[0]);
                pb.setString(2, b[1]);
                pb.setString(3, b[2]);
                pb.addBatch();
            }
            pb.executeBatch();

            conn.commit();
            System.out.println("[DB] Seed data inserted: 5 members, 10 books.");
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}

        try {
            DriverManager.getConnection(DB_URL_SHUTDOWN);
        } catch (SQLException e) {
            // Derby always throws XJ015 on clean shutdown — this is expected
            if ("XJ015".equals(e.getSQLState())) {
                System.out.println("[DB] Derby shut down cleanly.");
            } else {
                System.err.println("[DB] Shutdown error: " + e.getMessage());
            }
        }
    }
}
