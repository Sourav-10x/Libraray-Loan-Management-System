# Library Loan Management System  
### End-to-End JDBC Application with Transaction Management & Performance Evaluation

**Course:** CSE 3488 – Database Implementation in JDBC  
**University:** Siksha 'O' Anusandhan (ITER)  

---

# Project Overview

This project is a complete **console-driven JDBC application** developed using **Java** and **Apache Derby**. The system demonstrates:

- Explicit JDBC transaction management
- ACID property enforcement
- Commit, rollback, and savepoint handling
- Constraint validation and exception handling
- Performance benchmarking of JDBC strategies
- Layered software architecture
- Query optimization using indexes

The application simulates a real-world **Library Loan Management System** with transactional operations and built-in performance evaluation utilities.

---

# Objectives

The project was designed to:

- Implement a normalized relational database using Apache Derby
- Develop a layered JDBC application architecture
- Demonstrate explicit transaction boundaries
- Evaluate performance of multiple JDBC access strategies
- Benchmark insert/query/transaction execution patterns
- Preserve data consistency during failure scenarios

---

# Project Structure

```text
LibraryLoanSystem/
└── src/sourav/A1_diij/library/
    ├── connection/
    │   └── ConnectionManager.java
    │        → DB initialization, schema creation, indexes, seed data
    │
    ├── transaction/
    │   └── TransactionService.java
    │        → Commit/rollback/savepoint logic
    │
    ├── business/
    │   └── BusinessLogic.java
    │        → CRUD operations, loan/return processing, queries
    │
    ├── benchmark/
    │   └── PerformanceEvaluator.java
    │        → Benchmark suites, timing utilities, CSV reporting
    │
    │
    └── ui/
        └── MainApp.java
             → CLI workflow orchestration & user interaction
```

---

# Technologies Used

| Technology | Purpose |
|---|---|
| Java | Core application development |
| JDBC | Database connectivity |
| Apache Derby | Embedded relational database |
| SQL | Database operations |
| Java Collections | Data handling |
| System.nanoTime() | Performance benchmarking |

---

# Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Apache Derby | 10.16.1.1 | Embedded database |
| JDK | 11+ | Java runtime |

### Download Derby
https://db.apache.org/derby/derby_downloads.html

Required JAR:
- `derby.jar` → Embedded mode
- `derbyclient.jar` + `derbynet.jar` → Network mode (optional)

---

# Implementation Phases

---

# Phase 1 — Database Initialization & Schema Design

Implemented:
- Embedded Derby database configuration
- JDBC URL setup
- Automatic schema initialization
- Normalized table creation
- Primary key & foreign key constraints
- Unique constraints
- Index creation for optimization
- Baseline seed data insertion

### Tables Created
- Members
- Books
- Loans

### Indexes Created
- `idx_books_isbn`
- `idx_loans_member`
- `idx_loans_return`
- `idx_loans_due`

---

# Phase 2 — Transaction Management

Implemented:
- Explicit transaction boundaries
- Auto-commit disabled for critical operations
- Commit on successful execution
- Rollback on SQLException
- Savepoint support
- Constraint violation handling
- Transaction rollback demonstration

### Transaction Workflow
Loan processing includes:
1. Verify book availability
2. Insert loan record
3. Update book availability
4. Update member loan count
5. Commit transaction

If any step fails:
- Full rollback executes
- Data integrity is preserved

---

# Phase 3 — Core Application Workflow

Implemented a complete menu-driven CLI system supporting:

### Member Operations
- Register new members
- List all members

### Book Operations
- Add new books
- List available books

### Loan Operations
- Process loans
- Process returns
- View active loans
- View overdue books

### Benchmark Utilities
- Run performance benchmarks
- Demonstrate rollback scenarios

---

# Phase 4 — Performance Evaluation Framework

Implemented benchmark modules to compare JDBC strategies.

## Benchmark Suites

### 1. Insert Strategy Benchmark
Compares:
- Individual `executeUpdate()`
- Batch `addBatch()` + `executeBatch()`

Record volumes:
- 1,000 records
- 10,000 records

---

### 2. Query Strategy Benchmark
Compares:
- Full table scan
- Indexed lookup

---

### 3. Statement Type Benchmark
Compares:
- `Statement`
- `PreparedStatement`

Measures:
- Compilation overhead
- Execution efficiency

---

### 4. Transaction Granularity Benchmark
Compares:
- Per-operation commit
- Batched commit

Operations:
- 100+ transactional updates

---

# Benchmark Methodology

| Metric | Measurement |
|---|---|
| Execution Time | `System.nanoTime()` |
| Runs Per Test | 3–5 |
| Warm-up Phase | Implemented |
| Throughput | Operations/sec |
| Result Output | Console + CSV |

---

# Phase 5 — Documentation & Validation

Implemented:
- Graceful database shutdown
- Resource cleanup using try-with-resources
- Exception handling
- Constraint validation
- Duplicate entry testing
- Rollback validation
- Query performance verification

---

# Build & Run Instructions

---

# Compile

### Linux / Mac
```bash
javac -cp "lib/derby.jar" -d out \
src/main/java/com/library/**/*.java
```

### Windows
```bash
javac -cp "lib/derby.jar" -d out ^
src/main/java/com/library/**/*.java
```

---

# Run

### Linux / Mac
```bash
java -cp "out:lib/derby.jar" com.library.ui.MainApp
```

### Windows
```bash
java -cp "out;lib/derby.jar" com.library.ui.MainApp
```

---

# Sample CLI Session

```text
╔══════════════════════════════════════════════════╗
║   Library Loan Management System (JDBC + Derby) ║
╚══════════════════════════════════════════════════╝

[DB] Table created: Members
[DB] Table created: Books
[DB] Table created: Loans
[DB] Seed data inserted successfully.

MAIN MENU

[1] Register member
[2] List members
[3] Add book
[4] List books
[5] Process loan
[6] Process return
[7] Active loans by member
[8] Overdue books
[9] Demonstrate TX rollback
[10] Run performance benchmarks
[0] Exit

Choice: 5

Enter BookID: 1
Enter MemberID: 1
Loan period (days, default 14): 14

[TX] Loan processed successfully.
[TX] COMMIT executed.
```

---

# Database Schema

```sql
Members (
    MemberID INT PRIMARY KEY,
    Name VARCHAR(100),
    Email VARCHAR(100) UNIQUE,
    ActiveLoans INT
);

Books (
    BookID INT PRIMARY KEY,
    Title VARCHAR(200),
    Author VARCHAR(100),
    ISBN VARCHAR(30) UNIQUE,
    Available BOOLEAN
);

Loans (
    LoanID INT PRIMARY KEY,
    MemberID INT REFERENCES Members(MemberID),
    BookID INT REFERENCES Books(BookID),
    LoanDate DATE,
    DueDate DATE,
    ReturnDate DATE
);
```

---

# JDBC Concepts Demonstrated

- JDBC DriverManager
- Connection lifecycle management
- PreparedStatement
- Batch processing
- Transactions
- Savepoints
- SQL exception handling
- Indexing
- Query optimization
- Resource cleanup
- Performance benchmarking

---

# ACID Properties Demonstrated

| Property | Implementation |
|---|---|
| Atomicity | Rollback on failure |
| Consistency | Constraint enforcement |
| Isolation | Controlled transaction boundaries |
| Durability | Persistent commits |

---

# Derby-Specific Notes

- Embedded DB URL:
```text
jdbc:derby:librarydb;create=true
```

- Shutdown URL:
```text
jdbc:derby:librarydb;shutdown=true
```

- Derby throws `XJ015` during shutdown.  
This is expected behavior and handled gracefully.

- Runtime statistics can be enabled using:
```sql
CALL SYSCS_UTIL.SYSCS_SET_RUNTIMESTATISTICS(1);
```

---

# Key Observations from Benchmarks

| Benchmark | Observation |
|---|---|
| Batch Inserts | Faster than individual inserts |
| PreparedStatement | More efficient & secure |
| Indexed Queries | Significantly faster lookups |
| Batched Commits | Reduced transaction overhead |

---

# Future Enhancements

- GUI using JavaFX or Swing
- REST API integration
- Multi-user concurrency support
- Authentication system
- Migration to MySQL/PostgreSQL
- Web dashboard
- Dockerized deployment



# License

This project is developed for academic and educational purposes.

`
