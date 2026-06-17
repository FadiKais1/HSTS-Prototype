# Prototype Scope

The assignment requires a working prototype with:

- A basic server connected to a database skeleton.
- A questions table with at least 6 questions.
- A client running against the server.
- A graphical user interface.
- Display question list.
- Select and edit a question.
- Send an update request to the server.
- Read and display the updated question.

Therefore, the first prototype implements only question management.

## Implemented scenario

```text
Teacher opens QuestionBankPage
Teacher loads all questions
Teacher selects a question
Teacher edits the question content
Client sends UPDATE_QUESTION request to server
Server updates database
Client requests the updated question
GUI displays the updated content
```

## Not implemented in this prototype

These are part of the full system design, but not required for the first prototype:

- Login and permissions
- Exam building
- Automatic exam generation
- Exam approval
- Student exam execution
- Grading
- Reports
- Notifications
