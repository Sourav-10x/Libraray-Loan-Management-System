package com.library.transaction;

import com.library.connection.ConnectionManager;

import java.sql.*;

/**
 * TransactionService
 * ------------------
 * Implements explicit transaction boundaries, commit/rollback,
 * and savepoint logic for all data-modifying operations.
 *
 * Phase 2 – Transaction Management Implementation
 */
public class TransactionService {

    private final ConnectionManager cm = ConnectionManager.getInstance();

    // ── Process Loan (Multi-Step ACID Transaction) ────────────────────
    /**
     * Atomically processes a book loan:
     *  1. Verifies book availability
     *  2. Updates Books.Available = false
     *  3. Inserts a Loan record            ← Savepoint after this step
     *  4. Increments Members.ActiveLoans
     *
     * If step 4 fails, rolls back only to the savepoint (loan insert
     * is also reverted), then fully rolls back to maintain consistency.
     */
    public boolean processLoan(int bookId, int memberId, int daysLoan) {
        Connection conn = null;
        Savepoint sp = null;

        try {
            conn = cm.getConnection();
            conn.setAutoCommit(false);  // Explicit transaction start

            // ── Step 1: Verify book availability ──────────────────────
            if (!isBookAvailable(conn, bookId)) {
                System.out.println("[TX] Book #" + bookId + " is not available.");
                conn.rollback();
                return false;
            }

            // ── Step 2: Mark book as unavailable ──────────────────────
            updateBookAvailability(conn, bookId, false);

            // ── Step 3: Insert loan record ────────────────────────────
            insertLoan(conn, bookId, memberId, daysLoan);

            // Savepoint: loan inserted; next step is member update
            sp = conn.setSavepoint("AFTER_LOAN_INSERT");

            // ── Step 4: Increment member active loan count ────────────
            incrementMemberLoanCount(conn, memberId);

            // ── All steps successful: COMMIT ──────────────────────────
            conn.commit();
            System.out.println("[TX] Loan processed successfully. COMMITTED.");
            return true;

        } catch (SQLException e) {
            System.err.println("[TX] Error during loan processing: " + e.getMessage());
            try {
                if (conn != null) {
                    if (sp != null) {
                        conn.rollback(sp);  // Partial rollback to savepoint
                        System.out.println("[TX] Partial rollback to savepoint executed.");
                    }
                    conn.rollback();        // Full rollback
                    System.out.println("[TX] Full ROLLBACK executed. Data integrity preserved.");
                }
            } catch (SQLException re) {
                System.err.println("[TX] Rollback failed: " + re.getMessage());
            }
            return false;
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── Process Return ────────────────────────────────────────────────
    public boolean processReturn(int loanId) {
        Connection conn = null;
        try {
            conn = cm.getConnection();
            conn.setAutoCommit(false);

            // Find loan details
            int[] ids = getLoanDetails(conn, loanId);
            if (ids == null) {
                System.out.println("[TX] Loan #" + loanId + " not found or already returned.");
                conn.rollback();
                return false;
            }
            int bookId = ids[0], memberId = ids[1];

            // Update return date
            String sql = "UPDATE Loans SET ReturnDate = CURRENT_DATE WHERE LoanID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, loanId);
                ps.executeUpdate();
            }

            // Restore book availability
            updateBookAvailability(conn, bookId, true);

            // Decrement active loan count
            decrementMemberLoanCount(conn, memberId);

            conn.commit();
            System.out.println("[TX] Return processed. COMMITTED.");
            return true;

        } catch (SQLException e) {
            System.err.println("[TX] Return error: " + e.getMessage());
            rollback(conn);
            return false;
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── Demonstrate Isolation: Constraint Violation ───────────────────
    /**
     * Intentionally triggers a duplicate ISBN constraint violation
     * to demonstrate that ROLLBACK restores data consistency.
     */
    public void demonstrateConstraintRollback(Connection conn) {
        System.out.println("\n[TX-DEMO] Demonstrating constraint violation + rollback...");
        try {
            conn.setAutoCommit(false);

            String sql = "INSERT INTO Books (Title, Author, ISBN) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, "Duplicate Book");
                ps.setString(2, "Test Author");
                ps.setString(3, "978-0132350884");  // Already exists → will throw
                ps.executeUpdate();
            }
            conn.commit();

        } catch (SQLException e) {
            System.out.println("[TX-DEMO] Caught violation: " + e.getMessage());
            rollback(conn);
            System.out.println("[TX-DEMO] ROLLBACK executed. No partial data persisted.");
        } finally {
            resetAutoCommit(conn);
        }
    }

    // ── Helper Methods ────────────────────────────────────────────────
    private boolean isBookAvailable(Connection conn, int bookId) throws SQLException {
        String sql = "SELECT Available FROM Books WHERE BookID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("Available");
            }
        }
    }

    private void updateBookAvailability(Connection conn, int bookId, boolean available) throws SQLException {
        String sql = "UPDATE Books SET Available = ? WHERE BookID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, available);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        }
    }
    private void insertLoan(Connection conn, int bookId, int memberId, int days) throws SQLException {

        String sql =
            "INSERT INTO Loans (MemberID, BookID, DueDate) " +
            "VALUES (?, ?, DATE({fn TIMESTAMPADD(SQL_TSI_DAY, ?, CURRENT_TIMESTAMP)}))";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            ps.setInt(2, bookId);
            ps.setInt(3, days);

            ps.executeUpdate();
        }
    }

    private void incrementMemberLoanCount(Connection conn, int memberId) throws SQLException {
        String sql = "UPDATE Members SET ActiveLoans = ActiveLoans + 1 WHERE MemberID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Member #" + memberId + " not found.");
        }
    }

    private void decrementMemberLoanCount(Connection conn, int memberId) throws SQLException {
        String sql = "UPDATE Members SET ActiveLoans = CASE WHEN ActiveLoans > 0 THEN ActiveLoans - 1 ELSE 0 END WHERE MemberID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        }
    }

    private int[] getLoanDetails(Connection conn, int loanId) throws SQLException {
        String sql = "SELECT BookID, MemberID FROM Loans WHERE LoanID = ? AND ReturnDate IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new int[]{rs.getInt("BookID"), rs.getInt("MemberID")};
            }
        }
        return null;
    }

    private void rollback(Connection conn) {
        try { if (conn != null) conn.rollback(); }
        catch (SQLException e) { System.err.println("[TX] Rollback failed: " + e.getMessage()); }
    }

    private void resetAutoCommit(Connection conn) {
        try { if (conn != null) conn.setAutoCommit(true); }
        catch (SQLException ignored) {}
    }
}
