# HSTS Prototype Skeleton

This project is a first working prototype for the HSTS school exam-management system.

It implements the required prototype slice:

1. A basic server.
2. A database skeleton with at least 6 questions.
3. A client that communicates with the server.
4. A graphical user interface.
5. Displaying all questions.
6. Selecting and editing a question.
7. Sending an update request to the server.
8. Reading the updated question and displaying it again.

The implemented vertical flow is:

```text
QuestionBankPage
        ↓
QuestionClientController
        ↓
HSTSClient
        ↓
HSTSServer
        ↓
QuestionService
        ↓
QuestionRepository
        ↓
SQLite questions table
```

## Technology

- Java 17
- JavaFX GUI
- Maven
- SQLite database
- Simple socket-based client/server communication

## How to run

Open two terminals from the project root.

### Terminal 1: Start the server

```bash
mvn -q compile exec:java -Dexec.mainClass=hsts.server.MainServer
```

Or on Windows:

```bat
run-server.bat
```

### Terminal 2: Start the client GUI

```bash
mvn -q javafx:run
```

Or on Windows:

```bat
run-client.bat
```

## Demo flow

1. Start the server.
2. Start the client GUI.
3. Click **Load Questions**.
4. Select a question from the table.
5. Edit the question text.
6. Click **Update Question**.
7. The client sends the update request to the server.
8. The server updates the database.
9. The client reads the updated question and displays it again.

## Database

The database is created automatically at:

```text
data/hsts_prototype.db
```

The table is:

```sql
questions(question_id, content, topic, type, difficulty, status)
```

The project also includes the SQL file:

```text
database/init.sql
```

## Notes for the class diagram

This prototype implements only the required prototype part, not the full system.

Implemented classes from the design:

- `QuestionBankPage` as Boundary
- `QuestionService` as Control
- `Question` as Entity

Additional implementation/helper classes:

- `QuestionRepository`
- `DatabaseConnection`
- `DatabaseInitializer`
- `HSTSServer`
- `HSTSClient`
- `Request`
- `Response`
