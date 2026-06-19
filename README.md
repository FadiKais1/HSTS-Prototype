# HSTS Exam Management System - Part C Prototype

## 1. Project Overview

HSTS is a prototype for a school exam management system.

The full project is designed as a three-tier system:

```text
Boundary Layer  → GUI screens
Control Layer   → business logic and services
Entity Layer    → domain objects and database data
```

The complete system design includes teachers, students, coordinators, principals, exams, exam execution, grades, reports, notifications, and approvals.

This repository contains the working implementation prototype for Part C.
The prototype focuses on the Question Bank flow, because Part C requires a working client-server application connected to a database, with a GUI that allows the user to display questions, edit a selected question, send the update to the server, update the database, and read the updated question back.

---

## 2. What the Prototype Implements

The prototype implements the following required flow:

```text
Start MySQL Server
        ↓
Start Java server
        ↓
Start JavaFX client
        ↓
Load questions from MySQL database
        ↓
Display questions in the GUI
        ↓
Select a question
        ↓
Edit question details
        ↓
Send update request from client to server
        ↓
Server updates the MySQL database
        ↓
Server reads the updated question again
        ↓
Client displays the updated question
```

The database contains at least six sample questions.

Each question in the prototype is a multiple-choice question with four answer options.

---

## 3. Main Prototype Features

The current prototype supports:

* Loading all questions from the database.
* Displaying questions in a JavaFX table.
* Selecting a question from the table.
* Editing the question content.
* Editing the topic.
* Editing the difficulty.
* Editing the status: `ACTIVE` or `INACTIVE`.
* Editing the illustration path or URL.
* Editing four answer options.
* Choosing the correct answer using radio buttons.
* Sending the update request to the server.
* Updating the question in MySQL.
* Re-reading the updated question from MySQL.
* Displaying the updated question back in the GUI.

---

## 4. Tools and Technologies Used

### 4.1 JavaFX + FXML

The GUI is implemented using JavaFX and FXML.

FXML is used to define the layout of the Question Bank screen.
The controller handles the user actions such as loading questions, selecting a question, saving updates, and re-reading the question from the database.

Main files:

```text
src/main/resources/hsts/client/boundary/question-bank-page.fxml
src/main/java/hsts/client/boundary/QuestionBankPage.java
src/main/java/hsts/client/boundary/QuestionBankPageController.java
```

---

### 4.2 OCSF Client-Server Communication

The prototype uses OCSF-style client-server communication.

The OCSF framework classes are implemented inside the project:

```text
src/main/java/hsts/ocsf/AbstractClient.java
src/main/java/hsts/ocsf/AbstractServer.java
src/main/java/hsts/ocsf/ConnectionToClient.java
```

The application-specific client and server extend these OCSF classes:

```text
src/main/java/hsts/client/net/HSTSClient.java
src/main/java/hsts/server/net/HSTSServer.java
```

`HSTSClient` extends `AbstractClient`.

`HSTSServer` extends `AbstractServer`.

The client sends a `Request` object to the server.
The server handles the request and returns a `Response` object to the client.

---

### 4.3 DTO and Request/Response Objects

The prototype uses DTOs and request-response objects to transfer data between the client and the server.

Main files:

```text
src/main/java/hsts/common/Request.java
src/main/java/hsts/common/Response.java
src/main/java/hsts/common/RequestType.java
src/main/java/hsts/common/ResponseStatus.java
src/main/java/hsts/common/QuestionDTO.java
src/main/java/hsts/common/UpdateQuestionPayload.java
```

Purpose:

```text
Request              → sent from client to server
Response             → sent from server to client
QuestionDTO          → transfers question data to the client
UpdateQuestionPayload → transfers edited question data to the server
```

---

### 4.4 MySQL Server + JDBC

The prototype uses MySQL Server as the database engine.

The Java server connects to MySQL using JDBC.

Main database files:

```text
src/main/java/hsts/server/repository/DatabaseConnection.java
src/main/java/hsts/server/repository/DatabaseController.java
src/main/java/hsts/server/repository/DatabaseInitializer.java
src/main/java/hsts/server/repository/QuestionRepository.java
database/init.sql
```

The database name is:

```text
hsts_prototype
```

The table used by the prototype is:

```text
questions
```

The database is initialized automatically when the Java server starts.
If the table does not exist, the server creates it.
If the table is empty, the server inserts the sample questions.

---

## 5. Current Architecture Flow

The runtime flow of the prototype is:

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
DatabaseController
        ↓
DatabaseConnection
        ↓
MySQL Server / hsts_prototype database
        ↓
Response
        ↓
HSTSClient
        ↓
QuestionClientController
        ↓
QuestionBankPageController
        ↓
GUI updates
```

---

## 6. Detailed Question Update Flow

When the user updates a question, the flow is:

```text
User selects a question in the GUI
        ↓
QuestionBankPageController displays the selected question fields
        ↓
User edits content, topic, difficulty, status, illustration path, answer options, and correct answer
        ↓
User clicks "Save Full Question Update"
        ↓
QuestionBankPageController creates an updated QuestionDTO
        ↓
QuestionClientController creates an UpdateQuestionPayload
        ↓
HSTSClient sends a Request to the server
        ↓
HSTSServer receives the Request
        ↓
HSTSServer sends the request to QuestionService
        ↓
QuestionService validates and prepares the update
        ↓
QuestionRepository updates the questions table in MySQL
        ↓
QuestionRepository reads the updated question again
        ↓
HSTSServer returns a Response with the updated QuestionDTO
        ↓
HSTSClient receives the Response
        ↓
QuestionBankPageController updates the GUI
```

---

## 7. Project Structure

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
├── README.md
├── run-server.bat
├── run-client.bat
├── run-server.sh
└── run-client.sh
```

---

# 8. Full Run Guide - From Zero to Running the Prototype

This section explains how to run the project on a new computer starting from cloning the GitHub repository.

---

## Step 0 - Required Software Checklist

Before running the project, make sure the computer has:

```text
Java JDK 17 or newer
Apache Maven
Git
MySQL Server 8.x
MySQL Command Line Client or MySQL Workbench
VS Code or IntelliJ IDEA
```

---

## Step 1 - Check Java

Open PowerShell and run:

```powershell
java -version
```

Expected result:

```text
java version "17..."
```

or a newer Java version.

Then check the Java compiler:

```powershell
javac -version
```

Expected result:

```text
javac 17...
```

If Java is missing, install JDK 17 or newer.

---

## Step 2 - Check Maven

Run:

```powershell
mvn -version
```

Expected result:

```text
Apache Maven ...
Java version: 17...
```

If Maven is missing, install Maven and make sure it is added to the system PATH.

---

## Step 3 - Check Git

Run:

```powershell
git --version
```

Expected result:

```text
git version ...
```

If Git is missing, install Git for Windows.

---

## Step 4 - Check MySQL

Try:

```powershell
mysql --version
```

If this works, MySQL is available from PowerShell.

If this command does not work, MySQL may still be installed but not added to PATH. In that case, use the full path:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --version
```

You can also open:

```text
MySQL 8.0 Command Line Client
```

from the Windows Start Menu.

---

## Step 5 - Clone the Repository

Choose a folder where you want to place the project, for example Desktop:

```powershell
cd Desktop
```

Clone the repository:

```powershell
git clone https://github.com/FadiKais1/HSTS-Prototype.git
```

Enter the project folder:

```powershell
cd HSTS-Prototype
```

If the repository folder name is different, enter the folder that contains `pom.xml`.

---

## Step 6 - Create the MySQL Database

Open MySQL Command Line Client or run MySQL from PowerShell:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

Enter your MySQL root password.

Inside MySQL, run:

```sql
CREATE DATABASE IF NOT EXISTS hsts_prototype;
USE hsts_prototype;
SHOW DATABASES;
```

You should see:

```text
hsts_prototype
```

Then exit:

```sql
exit;
```

---

## Step 7 - Set MySQL Credentials for the Java Server

The project does not store the MySQL password inside the code.

Before running the server, set the MySQL credentials in PowerShell.

In the same PowerShell terminal where you will run the server, run:

```powershell
$env:HSTS_DB_USER="root"
$env:HSTS_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```

Replace `YOUR_MYSQL_PASSWORD` with your actual MySQL password.

Example:

```powershell
$env:HSTS_DB_USER="root"
$env:HSTS_DB_PASSWORD="123456"
```

Do not commit or share your real password.

If your password contains special characters, use single quotes:

```powershell
$env:HSTS_DB_PASSWORD='your_password_here'
```

Optional custom database URL:

```powershell
$env:HSTS_DB_URL="jdbc:mysql://localhost:3306/hsts_prototype?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
```

Usually this is not needed because the default URL already points to `localhost:3306/hsts_prototype`.

---

## Step 8 - Compile the Project

From the project root folder, run:

```powershell
mvn -q clean compile
```

If compilation succeeds, there will be no error message.

If there is an error, read the first error line carefully. Common problems are:

```text
Java version is too old
Maven is not installed
MySQL dependency did not download
Wrong folder, not inside the folder that contains pom.xml
```

---

## Step 9 - Start the Server

In PowerShell, make sure you are still inside the project folder.

Make sure the MySQL password variable is set:

```powershell
$env:HSTS_DB_USER="root"
$env:HSTS_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```

Then start the server:

```powershell
mvn -q compile exec:java "-Dexec.mainClass=hsts.server.MainServer"
```

Expected output:

```text
HSTS OCSF server started on port 5555
```

Keep this terminal open.

If you see:

```text
Access denied for user 'root'@'localhost' (using password: NO)
```

it means you forgot to set the password environment variable in this PowerShell terminal.

Set it again:

```powershell
$env:HSTS_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```

Then run the server again.

If you see:

```text
Address already in use: bind
```

it means the server is already running on port 5555. Stop the old server using `Ctrl + C`, or close the old server terminal.

---

## Step 10 - Start the Client

Open a second PowerShell terminal.

Go to the project folder again:

```powershell
cd path\to\HSTS-Prototype
```

Run the client:

```powershell
mvn -q javafx:run
```

The JavaFX Question Bank screen should open.

The client does not need the MySQL password because only the server connects to the database.

---

## Step 11 - Test the Prototype

In the GUI:

```text
1. Click "Load Questions".
2. Select a question from the table.
3. Edit the question content, topic, difficulty, status, illustration path, or answer options.
4. Choose the correct answer.
5. Click "Save Full Question Update".
6. Click "Re-read From Database".
7. Confirm that the updated data appears in the GUI.
```

---

## Step 12 - Verify the Update in MySQL

Open MySQL Command Line Client again:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

Then run:

```sql
USE hsts_prototype;

SELECT question_id, content, topic, difficulty, status
FROM questions;
```

You should see the questions stored in MySQL.

After editing a question from the GUI, run the same query again and confirm that the updated data appears.

---

# 9. Useful Commands

## Compile

```powershell
mvn -q clean compile
```

## Run server

```powershell
$env:HSTS_DB_USER="root"
$env:HSTS_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
mvn -q compile exec:java "-Dexec.mainClass=hsts.server.MainServer"
```

## Run client

```powershell
mvn -q javafx:run
```

## MySQL login

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p
```

## Check database content

```sql
USE hsts_prototype;

SELECT question_id, content, topic, difficulty, status
FROM questions;
```

---

# 10. Troubleshooting

## Problem: `mysql` is not recognized

Use the full path:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --version
```

or open MySQL Command Line Client from the Start Menu.

---

## Problem: `Access denied ... using password: NO`

The server tried to connect to MySQL without a password.

Fix:

```powershell
$env:HSTS_DB_USER="root"
$env:HSTS_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```

Then run the server again.

---

## Problem: `Access denied ... using password: YES`

The server used a password, but it is wrong.

Fix:

```text
Use the same password that works in MySQL Command Line Client.
```

Then set it again:

```powershell
$env:HSTS_DB_PASSWORD="correct_password_here"
```

---

## Problem: `Unknown database 'hsts_prototype'`

Create the database:

```sql
CREATE DATABASE IF NOT EXISTS hsts_prototype;
```

Then run the server again.

---

## Problem: `Address already in use: bind`

The server port is already used.

Fix:

```text
Stop the previous server with Ctrl + C.
```

Or find the process:

```powershell
netstat -ano | findstr :5555
```

Then stop it:

```powershell
taskkill /PID <PID_NUMBER> /F
```

---

## Problem: GUI still says SQLite

This means the GUI label was not updated.
Search for old text:

```powershell
Get-ChildItem -Path src\main -Recurse -File | Select-String -Pattern "SQLite"
```

Replace:

```text
Server-side SQLite database
```

with:

```text
MySQL Server database
```

Then compile and run again.

---

# 11. Git Commands for Submitting Changes

Check changed files:

```powershell
git status --short
```

Add relevant source files:

```powershell
git add pom.xml
git add database/init.sql
git add README.md
git add src/main/java
git add src/main/resources
```

Commit:

```powershell
git commit -m "Update prototype to use OCSF and MySQL database"
```

Push:

```powershell
git push
```

Check final status:

```powershell
git status
```

Expected result:

```text
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

---

# 12. What This Prototype Demonstrates

This prototype demonstrates the Part C technical implementation:

* JavaFX GUI.
* FXML layout.
* Client application.
* Server application.
* OCSF-style client-server communication.
* Request and Response transfer objects.
* MySQL database connection using JDBC.
* Server-side database initialization.
* Questions table with at least six questions.
* Editing a selected question.
* Updating the question through the server.
* Reading the updated question back from the database.
* Displaying the updated result in the GUI.

---

# 13. What Is Not Implemented in This Prototype

The full project design includes additional flows, including:

* Building full exams.
* Automatic exam generation.
* Exam approval by coordinator.
* Student exam execution.
* Saving student answers.
* Submitting exams.
* Grading.
* Reports.
* Notifications.

These flows are represented in the full system design and diagrams, but the working Part C prototype focuses on the required Question Bank update flow.
