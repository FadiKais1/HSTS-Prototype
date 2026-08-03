# HSTS — Run Pipeline

Group 7 · branch `final-polish` · 921 tests

---

## 1. Prerequisites

| | |
|---|---|
| Java | 17 or later |
| Maven | any 3.x |
| MySQL | 8 |
| OS | Windows PowerShell (commands below); adapt paths on macOS/Linux |

---

## 2. Build

```powershell
cd C:\Users\Fadi Kais\Projects\HSTS-Prototype
mvn clean package
```

Produces `target\G7_Server.jar` and `target\G7_Client.jar`, and runs all 921 tests
first. **A red build produces no jars**, so fix tests before continuing.

Use `mvn clean test` when you only want to check nothing broke — it is faster but
does not build the jars.

---

## 3. Create and load the database

Three commands, one at a time. Replace `root` with your MySQL user.

```powershell
mysql -u root -p -e "DROP DATABASE IF EXISTS hsts_prototype; CREATE DATABASE hsts_prototype;"
```

```powershell
cmd /c "mysql -u root -p hsts_prototype < database\init.sql"
```

```powershell
cmd /c "mysql -u root -p hsts_prototype < database\seed_demo_data.sql"
```

> PowerShell does not support `<` for input redirection, which is why these go
> through `cmd /c`.

The seed prints a verification table. Expect roughly:

| entity | rows |
|---|---|
| users | 36 |
| courses | 5 |
| questions | 70 |
| exams | 6 |
| executions | 7 |
| submissions | 132 |
| bots | 2 |
| bot_sources | 5 |
| bot_convos | 8 |
| bot_messages | 14 |

**Dropping the database discards any demo data you created by hand.** Skip the
drop and start the server instead if you want to keep it — schema migrations run
automatically at startup.

---

## 4. Start the server

```powershell
$env:HSTS_DB_USER = "root"
$env:HSTS_DB_PASSWORD = "your-mysql-password"
java -jar target\G7_Server.jar
```

Listens on port **5555**. Leave this window open — it prints connections and the
full cause of any failed request.

Optional: `HSTS_DB_URL` overrides the connection string
(default `jdbc:mysql://localhost:3306/hsts_prototype`).

---

## 5. Start the client

A **second** window, once per user you want signed in at the same time:

```powershell
java -jar target\G7_Client.jar
```

The connection screen appears first (NFR 15). Defaults are `localhost` and
`5555`; override with `HSTS_SERVER_HOST` and `HSTS_SERVER_PORT` when the server
runs on another machine.

---

## 6. Accounts

**Demo seed** — the accounts to use for most testing:

| Role | Email | Password |
|---|---|---|
| Teacher | `rania.haddad@hsts.local` | `Teacher!2026` |
| Teacher | `yosef.mizrahi@hsts.local` | `Teacher!2026` |
| Students | `student01@hsts.local` … `student30@hsts.local` | `Student!2026` |

**Development accounts** from `init.sql` — note the different password form:

| Role | Email | Password |
|---|---|---|
| Teacher | `teacher@hsts.local` | `TeacherDemo!2026` |
| Coordinator | `coordinator@hsts.local` | `CoordinatorDemo!2026` |
| Student | `student@hsts.local` | `StudentDemo!2026` |
| Principal | `principal@hsts.local` | `PrincipalDemo!2026` |

**Identity number** when starting an exam: `30000` + user id, so `student01` is
`300002001`. The `init.sql` student uses `123456789`.

**Useful pairings**

- `teacher@hsts.local` and `rania.haddad@hsts.local` both teach **MATH-10A** —
  use them for the two-teacher live-update tests.
- Rania also teaches **PHYS-10A**, so she is the account that exercises the
  subject selector.
- `coordinator@hsts.local` covers Mathematics, Physics and English.

---

## 7. Course Bot provider

**Offline (default).** No key, no internet. Answers by matching the question
against the teacher's uploaded sources. Reproducible, so preferred for recording
acceptance-test results.

**Gemini.** Set these in the **server** window before starting it:

```powershell
$env:HSTS_BOT_PROVIDER = "GEMINI"
$env:HSTS_GEMINI_API_KEY = "AIza..."
$env:HSTS_GEMINI_MODEL = "gemini-3.5-flash"
```

Keys come from `aistudio.google.com/apikey` and begin `AIza`. **Never commit a
key or put one in the submission** — set it at demo time only.

**Fallback if Gemini fails mid-demo.** Stop the server, then:

```powershell
Remove-Item Env:HSTS_BOT_PROVIDER
java -jar target\G7_Server.jar
```

Back on the offline bot in seconds, no code change. Rehearse this once.

---

## 8. Demonstration sequence

Roughly fifteen minutes, covering every role.

**Teacher** — `teacher@hsts.local`

1. **Question Bank** — identifiers show as `00101`, `00201` (req 33, 34). Pick a
   subject, then a course. Create a question. Edit one and use **Save as New
   Question** — a new identifier appears, the original is unchanged. **Delete
   Question** removes one from the bank while exams keep it.
2. **Exam Builder** — build an exam manually, or automatically from a
   topic/difficulty breakdown. Exam identifiers read `010101` (req 38, 39).
   **Save as New Exam** branches an approved exam into a fresh draft.
3. Submit for approval.

**Coordinator** — `coordinator@hsts.local`

4. **Approval Box** — approve or reject with a reason. The teacher's exam list
   updates without refreshing.

**Teacher**

5. **Exam Scheduling** — schedule the approved exam, set the execution code.
   Status follows the clock from SCHEDULED to OPEN while you watch.

**Students** — `student01` and `student02`, two clients

6. Enter the code and identity number, sit the exam, submit. The teacher's
   started and submitted counters move live.

**Teacher**

7. **Review Grades** — change a score by hand; a justification is required
   (req 39). Publish.

**Student**

8. **My Grades** — the grade, the marked exam, the teacher's feedback, and the
   adjustment reason (req 39, 40). A notification arrives on publication.
9. **Course Bot** — ask something covered by the material and something not;
   the second returns the no-answer message (req 48). History is per student
   (req 49).

**Principal** — `principal@hsts.local`

10. **Oversight** — Questions, Exams and Results tabs, read only (req 54).
11. **Reports** — pick a type and a target by name, load, then choose a second
    target to compare side by side with a summary (req 55).

---

## 9. Troubleshooting

| Symptom | Cause |
|---|---|
| `Unable to access jarfile` | `mvn clean test` was run instead of `package`, or the build failed |
| `Unknown database 'hsts'` | The database is `hsts_prototype` |
| `The '<' operator is reserved` | Use `cmd /c "... < file.sql"` |
| `git am` refuses to run | A previous one is stuck: `git am --abort` |
| Anything fails in the app | The **server window** prints the full cause chain |
| Student cannot start an exam | She has already attempted that execution |
| Bot declines a reasonable question | The material does not cover it — add a source as the teacher |

---

## 10. Publishing work

```powershell
git push origin final-polish
Remove-Item *.patch
```

`.patch` files are delivery envelopes, not part of the project — remove them
before submitting.
