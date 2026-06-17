# Class Diagram Mapping

This prototype maps to the 3-tier class diagram as follows.

## Boundary Layer

```text
QuestionBankPage
```

Implemented by:

```text
hsts.client.boundary.QuestionBankPage
```

Role: graphical screen for displaying, selecting, editing, and updating questions.

## Control Layer

```text
QuestionService
```

Implemented by:

```text
hsts.server.control.QuestionService
```

Role: business logic for retrieving and updating questions.

## Entity Layer

```text
Question
```

Implemented by:

```text
hsts.server.entity.Question
hsts.common.QuestionDTO
```

Role: represents the question data.

## Repository / Database Helper

```text
QuestionRepository
DatabaseConnection
DatabaseInitializer
```

These are implementation-level classes used to connect the control layer to the database.

## Client / Server Communication

```text
HSTSClient
HSTSServer
Request
Response
```

These are implementation-level classes used to pass messages between the GUI client and backend server.
