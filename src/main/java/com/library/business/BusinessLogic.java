package com.library.business;

import com.library.connection.ConnectionManager;
import com.library.transaction.TransactionService;

import java.sql.*;
import java.util.Scanner;

/**
 * BusinessLogic
 * -------------
 * Handles member registration, book management, loan queries,
 * and return processing. All DB calls use PreparedStatement.
 *
 * Phase 3 – Core Application Workflow
 */
public class BusinessLogic {

    private final ConnectionManager cm = ConnectionManager.getInstance();
    private final TransactionService txService = new TransactionService();

    // ── Member Registration ───────────────────────────────────────────
    public void registerMember(String name, String email) {
        String sql = "INSERT INTO Members (Name, Email) VALUES (?, ?)";
        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    System.out.println("[BL] Member registered. ID = " + keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BL] Register member error: " + e.getMessage());
        }
    }

    // ── Add Book ──────────────────────────────────────────────────────
    public void addBook(String title, String author, String isbn) {
        String sql = "INSERT INTO Books (Title, Author, ISBN) VALUES (?, ?, ?)";
        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, title);
            ps.setString(2, author);
            ps.setString(3, isbn);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    System.out.println("[BL] Book added. ID = " + keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[BL] Add book error: " + e.getMessage());
        }
    }

    // ── Process Loan (delegates to TransactionService) ────────────────
    public void processLoan(int bookId, int memberId, int days) {
        boolean ok = txService.processLoan(bookId, memberId, days);
        if (!ok) System.out.println("[BL] Loan could not be processed.");
    }

    // ── Process Return (delegates to TransactionService) ──────────────
    public void processReturn(int loanId) {
        boolean ok = txService.processReturn(loanId);
        if (!ok) System.out.println("[BL] Return could not be processed.");
    }

    // ── List All Members ──────────────────────────────────────────────
    public void listMembers() {
        String sql = "SELECT MemberID, Name, Email, ActiveLoans FROM Members ORDER BY MemberID";
        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n┌─────┬──────────────────────┬──────────────────────┬───────┐");
            System.out.printf("│ %-3s │ %-20s │ %-20s │ %-5s │%n", "ID", "Name", "Email", "Loans");
            System.out.println("├─────┼──────────────────────┼──────────────────────┼───────┤");
            while (rs.next()) {
                System.out.printf("│ %-3d │ %-20s │ %-20s │ %-5d │%n",
                        rs.getInt("MemberID"),
                        truncate(rs.getString("Name"), 20),
                        truncate(rs.getString("Email"), 20),
                        rs.getInt("ActiveLoans"));
            }
            System.out.println("└─────┴──────────────────────┴──────────────────────┴───────┘");
        } catch (SQLException e) {
            System.err.println("[BL] List members error: " + e.getMessage());
        }
    }

    // ── List All Books ────────────────────────────────────────────────
    public void listBooks() {
        String sql = "SELECT BookID, Title, Author, ISBN, Available FROM Books ORDER BY BookID";
        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n┌─────┬────────────────────────────┬──────────────────┬────────────────┬───────┐");
            System.out.printf("│ %-3s │ %-26s │ %-16s │ %-14s │ %-5s │%n",
                    "ID", "Title", "Author", "ISBN", "Avail");
            System.out.println("├─────┼────────────────────────────┼──────────────────┼────────────────┼───────┤");
            while (rs.next()) {
                System.out.printf("│ %-3d │ %-26s │ %-16s │ %-14s │ %-5s │%n",
                        rs.getInt("BookID"),
                        truncate(rs.getString("Title"), 26),
                        truncate(rs.getString("Author"), 16),
                        rs.getString("ISBN"),
                        rs.getBoolean("Available") ? "Yes" : "No");
            }
            System.out.println("└─────┴────────────────────────────┴──────────────────┴────────────────┴───────┘");
        } catch (SQLException e) {
            System.err.println("[BL] List books error: " + e.getMessage());
        }
    }

    // ── Active Loans by Member ────────────────────────────────────────
    public void queryActiveLoansByMember(int memberId) {
        String sql =
            "SELECT L.LoanID, B.Title, L.LoanDate, L.DueDate " +
            "FROM Loans L JOIN Books B ON L.BookID = B.BookID " +
            "WHERE L.MemberID = ? AND L.ReturnDate IS NULL " +
            "ORDER BY L.DueDate";

        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("\nActive loans for Member #" + memberId + ":");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("  LoanID=%-3d  Book=%-30s  Loaned=%s  Due=%s%n",
                            rs.getInt("LoanID"),
                            truncate(rs.getString("Title"), 30),
                            rs.getDate("LoanDate"),
                            rs.getDate("DueDate"));
                }
                if (!any) System.out.println("  (No active loans)");
            }
        } catch (SQLException e) {
            System.err.println("[BL] Query loans error: " + e.getMessage());
        }
    }

    // ── Overdue Books ─────────────────────────────────────────────────
    public void queryOverdueBooks() {
        String sql =
            "SELECT L.LoanID, M.Name, B.Title, L.DueDate " +
            "FROM Loans L " +
            "JOIN Members M ON L.MemberID = M.MemberID " +
            "JOIN Books   B ON L.BookID   = B.BookID " +
            "WHERE L.ReturnDate IS NULL AND L.DueDate < CURRENT_DATE " +
            "ORDER BY L.DueDate";

        try (Connection conn = cm.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\nOverdue Books:");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  LoanID=%-3d  Member=%-20s  Book=%-30s  DueDate=%s%n",
                        rs.getInt("LoanID"),
                        truncate(rs.getString("Name"), 20),
                        truncate(rs.getString("Title"), 30),
                        rs.getDate("DueDate"));
            }
            if (!any) System.out.println("  (No overdue books)");
        } catch (SQLException e) {
            System.err.println("[BL] Overdue query error: " + e.getMessage());
        }
    }

    // ── Demonstrate Constraint Rollback ───────────────────────────────
    public void demonstrateConstraintRollback() {
        try (Connection conn = cm.getConnection()) {
            txService.demonstrateConstraintRollback(conn);
        } catch (SQLException e) {
            System.err.println("[BL] Demo error: " + e.getMessage());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────
    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
