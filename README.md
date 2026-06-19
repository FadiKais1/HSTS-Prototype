# HSTS Prototype - Exam Management System

## Project Overview

HSTS is a prototype for a school exam management system.
The full system is designed as a three-tier architecture with:

* Boundary layer: GUI screens used by teachers, students, coordinators, and principals.
* Control layer: service classes that contain the business logic.
* Entity layer: domain objects such as Question, Exam, ExamExecution, ExamSubmission, StudentAnswer, Grade, and Report.

This repository contains the working prototype for Part C of the project.
The prototype focuses on the Question Bank flow, because Part C requires a client-server application connected to a database, with a GUI that allows the user to display questions, edit a selected question, send the update request to the server, update the database, and read the updated question back.

## Prototype Scope

The prototype implements the following required flow:

1. Start a server.
2. Connect a JavaFX client to the server.
3. Load questions from the database.
4. Display the questions in a GUI table.
5. Select a question.
6. Edit the question data.
7. Send an update request from the client to the server.
8. Update the question in the database.
9. Read the updated question again from the database.
10. Display the updated question back in the GUI.

The prototype database contains at least six sample questions.

## Main Features

The current prototype supports:

* Loading all questions from the database.
* Selecting a question from the questions table.
* Editing the question content.
* Editing the topic.
* Editing the difficulty.
* Editing the question status: ACTIVE or INACTIVE.
* Adding or editing an illustration path or URL.
* Editing four multiple-choice answer options.
* Selecting the correct answer using radio buttons.
* Saving the full updated question.
* Re-reading the updated question from the database and displaying it again.

All questions in the prototype are multiple-choice questions with exactly four answer options.

## Technologies and Course Tools Used

### JavaFX + FXML

The GUI is implemented using JavaFX and FXML.

The FXML file defines the visual layout of the Question Bank screen, while the controller class handles user actions such as loading questions, selecting a question, saving updates, and re-reading from the database.

Main files:

```text
src/main/resources/hsts/client/boundary/question-bank-page.fxml
src/main/java/hsts/client/boundary/QuestionBankPage.java
src/main/java/hsts/client/boundary/QuestionBankPageController.java
```

### OCSF-style Client-Server Communication

The prototype uses OCSF-style client-server communication.

The OCSF framework classes are included in:

```text
src/main/java/hsts/ocsf/AbstractClient.java
src/main/java/hsts/ocsf/AbstractServer.java
src/main/java/hsts/ocsf/ConnectionToClient.java
```

The system-specific client and server extend these OCSF classes:

```text
src/main/java/hsts/client/net/HSTSClient.java
src/main/java/hsts/server/net/HSTSServer.java
```

`HSTSClient` extends `AbstractClient`.

`HSTSServer` extends `AbstractServer`.

The client sends `Request` objects to the server, and the server returns `Response` objects back to the client.

### DTO and Request/Response Objects

The prototype uses DTO and request-response objects to transfer data between the client and server.

Main files:

```text
src/main/java/hsts/common/Request.java
src/main/java/hsts/common/Response.java
src/main/java/hsts/common/RequestType.java
src/main/java/hsts/common/ResponseStatus.java
src/main/java/hsts/common/QuestionDTO.java
src/main/java/hsts/common/UpdateQuestionPayload.java
```

`QuestionDTO` is used to transfer question data to the client without exposing the internal database access logic.

`UpdateQuestionPayload` is used when the client sends an update request to the server.

### JDBC / SQLite Database

The prototype uses SQLite as a lightweight local database.

The database is initialized automatically when the server starts.

Main database files:

```text
src/main/java/hsts/server/repository/DatabaseInitializer.java
src/main/java/hsts/server/repository/DatabaseConnection.java
src/main/java/hsts/server/repository/DatabaseController.java
src/main/java/hsts/server/repository/QuestionRepository.java
database/init.sql
```

The database file is generated locally under:

```text
data/hsts_prototype.db
```

This generated database file should not be committed to GitHub.

## Architecture Flow

The main prototype flow is:

```text
question-bank-page.fxml
        ↓
QuestionBankPageController
        ↓
QuestionClientController
        ↓
HSTSClient extends AbstractClient
        ↓
Request
        ↓
HSTSServer extends AbstractServer
        ↓
QuestionService
        ↓
QuestionRepository
        ↓
DatabaseController / DatabaseConnection
        ↓
SQLite database
        ↓
Response
        ↓
HSTSClient
        ↓
QuestionBankPageController
        ↓
GUI updates
```

## Detailed Update Flow

When the user updates a question, the flow is:

```text
Teacher selects a question in the GUI
        ↓
QuestionBankPageController displays the editable fields
        ↓
Teacher edits content, topic, difficulty, status, illustration path, answer options, and correct answer
        ↓
Teacher clicks "Save Full Question Update"
        ↓
QuestionBankPageController creates a QuestionDTO
        ↓
QuestionClientController creates an UpdateQuestionPayload
        ↓
HSTSClient sends a Request to HSTSServer
        ↓
HSTSServer receives the request and delegates it to QuestionService
        ↓
QuestionService validates the update
        ↓
QuestionRepository updates the questions table
        ↓
QuestionRepository reads the updated question again
        ↓
HSTSServer returns a Response with the updated QuestionDTO
        ↓
The client displays the updated question in the GUI
```

## Project Structure

```text
HSTS_Prototype_Skeleton/
│
├── database/
│   └── init.sql
│
├── src/main/java/hsts/
│   │
│   ├── client/
│   │   ├── MainClient.java
│   │   ├── boundary/
│   │   │   ├── QuestionBankPage.java
│   │   │   └── QuestionBankPageController.java
│   │   ├── control/
│   │   │   └── QuestionClientController.java
│   │   └── net/
│   │       └── HSTSClient.java
│   │
│   ├── common/
│   │   ├── QuestionDTO.java
│   │   ├── Request.java
│   │   ├── RequestType.java
│   │   ├── Response.java
│   │   ├── ResponseStatus.java
│   │   └── UpdateQuestionPayload.java
│   │
│   ├── ocsf/
│   │   ├── AbstractClient.java
│   │   ├── AbstractServer.java
│   │   └── ConnectionToClient.java
│   │
│   └── server/
│       ├── MainServer.java
│       ├── control/
│       │   └── QuestionService.java
│       ├── entity/
│       │   └── Question.java
│       ├── net/
│       │   └── HSTSServer.java
│       └── repository/
│           ├── DatabaseConnection.java
│           ├── DatabaseController.java
│           ├── DatabaseInitializer.java
│           └── QuestionRepository.java
│
├── src/main/resources/hsts/client/boundary/
│   └── question-bank-page.fxml
│
├── pom.xml
└── README.md
```

## How to Run the Prototype

### 1. Open PowerShell in the project folder

```powershell
cd "C:\Users\Fadi Kais\Desktop\HSTS_Prototype_Skeleton\HSTS_Prototype_Skeleton"
```

### 2. Start the server

Run:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=hsts.server.MainServer"
```

Expected output:

```text
HSTS OCSF server started on port 5555
```

Keep this terminal open.

If you get:

```text
Address already in use: bind
```

it means the server is already running on port 5555. Either use the existing server terminal or stop it with `Ctrl + C`.

### 3. Start the client

Open a second PowerShell terminal in the same project folder and run:

```powershell
mvn -q javafx:run
```

The JavaFX Question Bank screen should open.

## How to Use the Prototype

1. Click `Load Questions`.
2. Select a question from the table.
3. Edit the question content or attributes.
4. Change the status to `ACTIVE` or `INACTIVE`.
5. Edit the illustration path or URL.
6. Edit the four answer options.
7. Select the correct answer radio button.
8. Click `Save Full Question Update`.
9. The system updates the database and re-reads the updated question.
10. The updated values are displayed again in the GUI.

## Database Notes

The local SQLite database is generated automatically.

If old data causes problems after schema changes, stop the server and delete:

```text
data/hsts_prototype.db
```

Then run the server again.
The database will be recreated automatically with the required questions.

Do not commit the generated database file.

## Git Notes

Before committing, check the status:

```powershell
git status
```

Add project source changes:

```powershell
git add pom.xml
git add src/main/java
git add src/main/resources
git add database/init.sql
git add README.md
```

Commit:

```powershell
git commit -m "Update README and document FXML OCSF question editing prototype"
```

Push:

```powershell
git push
```

Confirm everything is pushed:

```powershell
git status
```

Expected result:

```text
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

## What This Prototype Demonstrates

This prototype demonstrates the technical requirement of Part C:

* A working JavaFX GUI.
* A client application.
* A server application.
* OCSF-style client-server communication.
* Request and Response transfer objects.
* A persistent database with a questions table.
* At least six questions.
* Editing a selected question.
* Updating the database through the server.
* Reading the updated question back from the database.
* Displaying the updated result to the user.

## What Is Not Implemented in the Prototype

The full project design includes many additional use cases, such as:

* Building full exams.
* Approving exams.
* Executing exams by students.
* Saving student answers.
* Submitting exams.
* Grading.
* Reports.
* Notifications.

These flows are represented in the full system design and diagrams, but the working Part C prototype focuses on the required question-bank update flow.
