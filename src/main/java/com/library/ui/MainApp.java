package com.library.ui;

import com.library.benchmark.PerformanceEvaluator;
import com.library.business.BusinessLogic;
import com.library.connection.ConnectionManager;

import java.util.Scanner;

/**
 * MainApp
 * -------
 * Console-driven CLI for the Library Loan Management System.
 * Orchestrates all workflows: members, books, loans, benchmarks.
 *
 * Phase 3 – Core Application Workflow (CLI)
 *
 * GitHub Repo:
 * TODO: Replace below with your actual repository URL
 * https://github.com/<YOUR_USERNAME>/LibraryLoanSystem
 */
public class MainApp {

    private static final BusinessLogic bl   = new BusinessLogic();
    private static final PerformanceEvaluator pe = new PerformanceEvaluator();
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Library Loan Management System (JDBC + Derby) ║");
        System.out.println("║   CSE 3488 – Database Implementation in JDBC    ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        // ── Initialize DB ─────────────────────────────────────────────
        try {
            ConnectionManager.getInstance().initializeDatabase();
        } catch (Exception e) {
            System.err.println("FATAL: Cannot initialize database. " + e.getMessage());
            System.exit(1);
        }

        // ── Main Menu Loop ────────────────────────────────────────────
        boolean running = true;
        while (running) {
            printMenu();
            String choice = sc.nextLine().trim();

            switch (choice) {
                // ── Members ───────────────────────────────────────────
                case "1":
                    System.out.print("Enter name: ");
                    String name = sc.nextLine().trim();
                    System.out.print("Enter email: ");
                    String email = sc.nextLine().trim();
                    bl.registerMember(name, email);
                    break;

                case "2":
                    bl.listMembers();
                    break;

                // ── Books ─────────────────────────────────────────────
                case "3":
                    System.out.print("Enter title: ");
                    String title = sc.nextLine().trim();
                    System.out.print("Enter author: ");
                    String author = sc.nextLine().trim();
                    System.out.print("Enter ISBN: ");
                    String isbn = sc.nextLine().trim();
                    bl.addBook(title, author, isbn);
                    break;

                case "4":
                    bl.listBooks();
                    break;

                // ── Loans ─────────────────────────────────────────────
                case "5":
                    System.out.print("Enter BookID: ");
                    int bookId = readInt();
                    System.out.print("Enter MemberID: ");
                    int memberId = readInt();
                    System.out.print("Loan period (days, default 14): ");
                    String daysStr = sc.nextLine().trim();
                    int days = daysStr.isEmpty() ? 14 : Integer.parseInt(daysStr);
                    bl.processLoan(bookId, memberId, days);
                    break;

                case "6":
                    System.out.print("Enter LoanID to return: ");
                    int loanId = readInt();
                    bl.processReturn(loanId);
                    break;

                case "7":
                    System.out.print("Enter MemberID: ");
                    int mid = readInt();
                    bl.queryActiveLoansByMember(mid);
                    break;

                case "8":
                    bl.queryOverdueBooks();
                    break;

                // ── Demos & Benchmarks ────────────────────────────────
                case "9":
                    bl.demonstrateConstraintRollback();
                    break;

                case "10":
                    pe.runAllBenchmarks();
                    break;

                // ── Exit ──────────────────────────────────────────────
                case "0":
                    System.out.println("\nShutting down...");
                    ConnectionManager.getInstance().shutdown();
                    running = false;
                    break;

                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }

        System.out.println("Goodbye!");
    }

    // ── Menu Printer ──────────────────────────────────────────────────
    private static void printMenu() {
        System.out.println("\n┌────────────────────────────────────────┐");
        System.out.println("│          MAIN MENU                     │");
        System.out.println("├────────────────────────────────────────┤");
        System.out.println("│  Members                               │");
        System.out.println("│   [1] Register new member              │");
        System.out.println("│   [2] List all members                 │");
        System.out.println("│  Books                                 │");
        System.out.println("│   [3] Add new book                     │");
        System.out.println("│   [4] List all books                   │");
        System.out.println("│  Loans                                 │");
        System.out.println("│   [5] Process loan                     │");
        System.out.println("│   [6] Process return                   │");
        System.out.println("│   [7] Active loans by member           │");
        System.out.println("│   [8] Overdue books                    │");
        System.out.println("│  Demos & Benchmarks                    │");
        System.out.println("│   [9] Demonstrate TX rollback          │");
        System.out.println("│  [10] Run performance benchmarks       │");
        System.out.println("│   [0] Exit                             │");
        System.out.println("└────────────────────────────────────────┘");
        System.out.print("Choice: ");
    }

    private static int readInt() {
        try { return Integer.parseInt(sc.nextLine().trim()); }
        catch (NumberFormatException e) { return -1; }
    }
}
