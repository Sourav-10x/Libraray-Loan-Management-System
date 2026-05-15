package com.library.benchmark;

import com.library.connection.ConnectionManager;

import java.sql.*;
import java.util.*;

/**
 * PerformanceEvaluator
 * --------------------
 * Benchmarks multiple JDBC access patterns and produces
 * a structured comparative performance report.
 *
 * Phase 4 – Performance Evaluation Framework
 *
 * Test Suites:
 *   1. Insert Strategy   – Individual vs Batch inserts (1,000 / 10,000 records)
 *   2. Query Strategy    – Full-table scan vs Indexed lookup
 *   3. Statement Type    – Statement (concat) vs PreparedStatement
 *   4. Transaction Gran. – Per-operation commit vs Batched commit
 */
public class PerformanceEvaluator {

    private static final int RUNS     = 5;   // Runs per test for averaging
    private static final int WARMUP   = 100; // Warm-up iterations

    private final ConnectionManager cm = ConnectionManager.getInstance();

    // ── Entry Point ───────────────────────────────────────────────────
    public void runAllBenchmarks() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  JDBC PERFORMANCE EVALUATION REPORT");
        System.out.println("  Library Loan Management System – Apache Derby");
        System.out.println("=".repeat(80));

        List<BenchmarkResult> results = new ArrayList<>();

        try {
            warmUp();

            // Suite 1: Insert Strategy
            results.addAll(benchmarkInsertStrategy(1_000));
            results.addAll(benchmarkInsertStrategy(10_000));

            // Suite 2: Query Strategy
            results.addAll(benchmarkQueryStrategy());

            // Suite 3: Statement Type
            results.addAll(benchmarkStatementType());

            // Suite 4: Transaction Granularity
            results.addAll(benchmarkTransactionGranularity());

        } catch (SQLException e) {
            System.err.println("[BENCH] Fatal error: " + e.getMessage());
        }

        printReport(results);
        exportCsv(results);
    }

    // ── Warm-Up Phase ─────────────────────────────────────────────────
    private void warmUp() throws SQLException {
        System.out.println("\n[BENCH] Running warm-up phase (" + WARMUP + " iterations)...");
        Connection conn = cm.getConnection();
        String sql = "SELECT COUNT(*) FROM Members";
        for (int i = 0; i < WARMUP; i++) {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
        System.out.println("[BENCH] Warm-up complete. JVM JIT and Derby buffer cache primed.");
    }

    // ── Suite 1: Insert Strategy ──────────────────────────────────────
    private List<BenchmarkResult> benchmarkInsertStrategy(int count) throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        String benchTable = "BenchInsert";
        Connection conn = cm.getConnection();

        // Create temp table for benchmarking
        ensureBenchTable(conn, benchTable);

        // -- 1a: Individual inserts --
        double[] individualTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearTable(conn, benchTable);
            conn.setAutoCommit(false);
            long start = System.nanoTime();

            String sql = "INSERT INTO " + benchTable + " (Val) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < count; i++) {
                    ps.setInt(1, i);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            individualTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
            conn.setAutoCommit(true);
        }

        // -- 1b: Batch inserts --
        double[] batchTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearTable(conn, benchTable);
            conn.setAutoCommit(false);
            long start = System.nanoTime();

            String sql = "INSERT INTO " + benchTable + " (Val) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < count; i++) {
                    ps.setInt(1, i);
                    ps.addBatch();
                    if (i % 500 == 0) ps.executeBatch(); // Flush every 500
                }
                ps.executeBatch();
            }
            conn.commit();
            batchTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
            conn.setAutoCommit(true);
        }

        results.add(new BenchmarkResult(
                "Individual Inserts", count + " records",
                mean(individualTimes), stddev(individualTimes),
                count / (mean(individualTimes) / 1000.0),
                "Separate executeUpdate() per row; high round-trip overhead"));

        results.add(new BenchmarkResult(
                "Batch Inserts", count + " records",
                mean(batchTimes), stddev(batchTimes),
                count / (mean(batchTimes) / 1000.0),
                "addBatch() + executeBatch(); amortized I/O; significantly faster"));

        return results;
    }

    // ── Suite 2: Query Strategy ───────────────────────────────────────
    private List<BenchmarkResult> benchmarkQueryStrategy() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        Connection conn = cm.getConnection();
        int runs = RUNS * 2; // More runs for query tests

        // Full-table scan
        double[] scanTimes = new double[runs];
        for (int r = 0; r < runs; r++) {
            long start = System.nanoTime();
            String sql = "SELECT * FROM Loans WHERE ReturnDate IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { /* consume */ }
            }
            scanTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
        }

        // Indexed lookup (MemberID index)
        double[] indexTimes = new double[runs];
        for (int r = 0; r < runs; r++) {
            long start = System.nanoTime();
            String sql = "SELECT * FROM Loans WHERE MemberID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) { /* consume */ }
                }
            }
            indexTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
        }

        results.add(new BenchmarkResult(
                "Full-Table Scan", "Loans (ReturnDate IS NULL)",
                mean(scanTimes), stddev(scanTimes),
                1000.0 / mean(scanTimes),
                "No index used; Derby scans all rows sequentially"));

        results.add(new BenchmarkResult(
                "Indexed Lookup", "Loans (MemberID = 1)",
                mean(indexTimes), stddev(indexTimes),
                1000.0 / mean(indexTimes),
                "idx_loans_member used; Derby performs B-tree seek"));

        return results;
    }

    // ── Suite 3: Statement Type ───────────────────────────────────────
    private List<BenchmarkResult> benchmarkStatementType() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        Connection conn = cm.getConnection();
        int iterations = 200;

        // Plain Statement (string concat)
        double[] stmtTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            try (Statement st = conn.createStatement()) {
                for (int i = 0; i < iterations; i++) {
                    // Intentional string concat (SQL injection risk; never do this in production)
                    try (ResultSet rs = st.executeQuery(
                            "SELECT * FROM Books WHERE BookID = " + (i % 10 + 1))) {
                        while (rs.next()) { /* consume */ }
                    }
                }
            }
            stmtTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
        }

        // PreparedStatement (pre-compiled)
        double[] psTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            String sql = "SELECT * FROM Books WHERE BookID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < iterations; i++) {
                    ps.setInt(1, i % 10 + 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) { /* consume */ }
                    }
                }
            }
            psTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
        }

        results.add(new BenchmarkResult(
                "Statement (concat)", iterations + " queries",
                mean(stmtTimes), stddev(stmtTimes),
                iterations / (mean(stmtTimes) / 1000.0),
                "Parsed and planned on every call; SQL injection risk"));

        results.add(new BenchmarkResult(
                "PreparedStatement", iterations + " queries",
                mean(psTimes), stddev(psTimes),
                iterations / (mean(psTimes) / 1000.0),
                "Compiled once; Derby reuses plan; safer and faster"));

        return results;
    }

    // ── Suite 4: Transaction Granularity ──────────────────────────────
    private List<BenchmarkResult> benchmarkTransactionGranularity() throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        String benchTable = "BenchInsert";
        Connection conn = cm.getConnection();
        ensureBenchTable(conn, benchTable);
        int ops = 100;

        // Per-operation commit
        double[] perOpTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearTable(conn, benchTable);
            long start = System.nanoTime();
            String sql = "INSERT INTO " + benchTable + " (Val) VALUES (?)";
            for (int i = 0; i < ops; i++) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, i);
                    ps.executeUpdate();
                }
                conn.commit(); // Commit after each insert
            }
            perOpTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
            conn.setAutoCommit(true);
        }

        // Batched commit (all 100 in one transaction)
        double[] batchedTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearTable(conn, benchTable);
            conn.setAutoCommit(false);
            long start = System.nanoTime();
            String sql = "INSERT INTO " + benchTable + " (Val) VALUES (?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < ops; i++) {
                    ps.setInt(1, i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            batchedTimes[r] = (System.nanoTime() - start) / 1_000_000.0;
            conn.setAutoCommit(true);
        }

        results.add(new BenchmarkResult(
                "Per-Op Commit", ops + " operations",
                mean(perOpTimes), stddev(perOpTimes),
                ops / (mean(perOpTimes) / 1000.0),
                "100 separate commits; maximum durability, high I/O cost"));

        results.add(new BenchmarkResult(
                "Batched Commit", ops + " operations",
                mean(batchedTimes), stddev(batchedTimes),
                ops / (mean(batchedTimes) / 1000.0),
                "Single commit for all 100 ops; dramatically lower I/O"));

        return results;
    }

    // ── Report Printer ────────────────────────────────────────────────
    private void printReport(List<BenchmarkResult> results) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("  PERFORMANCE SUMMARY TABLE");
        System.out.println("=".repeat(120));
        System.out.printf("  %-28s %-26s %10s %8s %12s  %-30s%n",
                "Operation", "Complexity", "Avg (ms)", "±SD", "Throughput", "Observations");
        System.out.println("  " + "-".repeat(118));

        for (BenchmarkResult r : results) {
            System.out.printf("  %-28s %-26s %10.2f %8.2f %12.1f  %-30s%n",
                    r.operation,
                    r.complexity,
                    r.avgMs,
                    r.sdMs,
                    r.throughputOpsPerSec,
                    r.observation.length() > 30 ? r.observation.substring(0, 28) + ".." : r.observation);
        }
        System.out.println("=".repeat(120));
    }

    // ── CSV Export ────────────────────────────────────────────────────
    private void exportCsv(List<BenchmarkResult> results) {
        System.out.println("\n  CSV OUTPUT (paste into spreadsheet):");
        System.out.println("  Operation,Complexity,Avg_ms,SD_ms,Throughput_ops_per_sec,Observations");
        for (BenchmarkResult r : results) {
            System.out.printf("  \"%s\",\"%s\",%.2f,%.2f,%.1f,\"%s\"%n",
                    r.operation, r.complexity, r.avgMs, r.sdMs,
                    r.throughputOpsPerSec, r.observation);
        }
    }

    // ── Utility Methods ───────────────────────────────────────────────
    private void ensureBenchTable(Connection conn, String name) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, "APP", name.toUpperCase(), null)) {
            if (!rs.next()) {
                try (Statement st = conn.createStatement()) {
                    st.execute("CREATE TABLE " + name + " (ID INT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, Val INT)");
                }
            }
        }
    }

    private void clearTable(Connection conn, String name) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM " + name);
        }
    }

    private double mean(double[] data) {
        double sum = 0;
        for (double d : data) sum += d;
        return sum / data.length;
    }

    private double stddev(double[] data) {
        double m = mean(data);
        double variance = 0;
        for (double d : data) variance += (d - m) * (d - m);
        return Math.sqrt(variance / data.length);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────
    public static class BenchmarkResult {
        final String operation;
        final String complexity;
        final double avgMs;
        final double sdMs;
        final double throughputOpsPerSec;
        final String observation;

        BenchmarkResult(String op, String cmp, double avg, double sd, double tps, String obs) {
            this.operation = op;
            this.complexity = cmp;
            this.avgMs = avg;
            this.sdMs = sd;
            this.throughputOpsPerSec = tps;
            this.observation = obs;
        }
    }
}
