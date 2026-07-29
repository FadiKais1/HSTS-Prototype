# HSTS Assignment 3

HSTS is a Java 17 client/server exam-management system. The JavaFX client and
the MySQL-backed server communicate over TCP through OCSF. The submission
artifacts are two independently runnable JARs:

- `target/G7_Server.jar`
- `target/G7_Client.jar`

## Prerequisites

- JDK 17 (the `java` command must resolve to that JDK).
- MySQL 8.x.
- Windows for the packaged client, because `G7_Client.jar` contains the
  Windows JavaFX native libraries.
- Network access from the client computer to the server host and port.

## Build

From the repository root:

```powershell
mvn clean package
```

The build runs the complete test suite and the packaging verifier before it
produces the two JARs above. Maven is not needed to run the packaged JARs.

## Database setup

For a fresh database, run `database/init.sql` once as a MySQL account allowed
to create the `hsts_prototype` database. The server then performs additive,
idempotent compatibility migrations at startup. Existing business data is not
replaced by those migrations.

The server reads these settings, with system properties taking precedence over
environment variables where a matching property exists:

| Environment variable | Purpose | Default |
|---|---|---|
| `HSTS_DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/hsts_prototype?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `HSTS_DB_USER` | MySQL user | `root` |
| `HSTS_DB_PASSWORD` | MySQL password | empty |
| `HSTS_SERVER_PORT` | TCP listening port | `5555` |

Do not commit a real database password. Example PowerShell configuration:

```powershell
$env:HSTS_DB_URL = 'jdbc:mysql://localhost:3306/hsts_prototype?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:HSTS_DB_USER = 'hsts_app'
$env:HSTS_DB_PASSWORD = '<database-password>'
$env:HSTS_SERVER_PORT = '5555'
java -jar target/G7_Server.jar
```

Start the server before the client. On the client computer:

```powershell
$env:HSTS_SERVER_HOST = '<server-host-or-ip>'
$env:HSTS_SERVER_PORT = '5555'
java -jar target/G7_Client.jar
```

The client also accepts Java system properties `hsts.server.host` and
`hsts.server.port`; they take precedence over the corresponding environment
variables. The server accepts `hsts.server.port` with the same precedence.

## Course Bot provider

If `HSTS_BOT_PROVIDER` is absent or set to `DETERMINISTIC`, the server uses the
offline deterministic provider. This is the recommended grading/demo mode and
requires no network or API key.

To use Gemini explicitly:

```powershell
$env:HSTS_BOT_PROVIDER = 'GEMINI'
$env:HSTS_GEMINI_API_KEY = '<gemini-api-key>'
$env:HSTS_GEMINI_MODEL = 'gemini-3.5-flash-lite'
$env:HSTS_GEMINI_TIMEOUT_SECONDS = '30'
java -jar target/G7_Server.jar
```

`HSTS_GEMINI_MODEL` defaults to `gemini-3.5-flash-lite` and
`HSTS_GEMINI_TIMEOUT_SECONDS` defaults to `30`. `HSTS_GEMINI_BASE_URL` is an
optional HTTPS endpoint override and should normally remain unset. An explicit
Gemini failure is reported; it does not silently fall back to deterministic
answers. Never commit or print the API key.

## Development accounts

The non-overwriting development seed provides these local demonstration
accounts. These passwords are demo credentials only and must not be reused in
production.

| Role | Email | Password |
|---|---|---|
| Student | `student@hsts.local` | `StudentDemo!2026` |
| Teacher | `teacher@hsts.local` | `TeacherDemo!2026` |
| Coordinator | `coordinator@hsts.local` | `CoordinatorDemo!2026` |
| Principal | `principal@hsts.local` | `PrincipalDemo!2026` |

For the seeded Student execution demonstration, the identity-confirmation
value is `123456789`. Only its PBKDF2 hash is stored. User passwords are also
stored only as salted PBKDF2 hashes.

## Role workflows

- Student: join an open exam using its four-character code, confirm identity,
  start or resume, save answers, submit, view published grades and reviewed
  copies, and use active Course Bots when enrolled and not taking an active
  exam in that course.
- Teacher: manage versioned questions and secure illustrations, build manual or
  automatic exams, submit them for approval, schedule approved versions,
  review/publish results, view reports, and manage Course Bot sources.
- Coordinator: use the Teacher workflow within assigned subjects and explicitly
  approve or reject pending exams. Approval is never automatic.
- Principal: view global questions, exams, executions, published results, and
  comparison reports without mutation controls.

Student profile, class/grade, and course-enrollment data are owned by external
school systems. HSTS reads those records for authorization and identity checks;
it does not provide enrollment or profile mutation screens or routes. The
development Student/profile/enrollment rows are isolated, non-overwriting
compatibility data for the local demonstration.

## Operational notes

- One active session is allowed per user. Logout and connection cleanup release
  the session.
- Only approved exact exam versions can be scheduled.
- Question and exam versions are immutable snapshots; approved content is never
  edited in place.
- Results become visible to Students only after Teacher/Coordinator publication.
- Course Bot source uploads are bounded and extracted on the server. The Gemini
  adapter is server-only.

For a clean acceptance run, use a fresh `hsts_prototype` database, start the
server, start one or more clients, and exercise each role with the accounts
above.
