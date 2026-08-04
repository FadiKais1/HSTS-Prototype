# HSTS — Complete Fix Checklist

Branch `final-polish` · 928 tests passing · 53 commits

Everything below was implemented, built green, and confirmed working in the
running application.

---

## 1. The requested list

| # | Item | Requirement | How | ✔ |
|---|---|---|---|---|
| 1 | Delete a question from the bank | — | Soft delete: hidden from the bank, kept in exams that use it | ☑ |
| 2 | Editing a question creates a new one | 33, 34 | **Save as New Question** beside the existing versioned update | ☑ |
| 3 | Teacher with courses across subjects | 15, 19 | Subject selector narrows the course list in the Exam Builder | ☑ |
| 4 | Editing an exam creates a new one | 38, 39 | **Save as New Exam**; the original keeps its approval | ☑ |
| 5 | Screens do not update in real time | NFR 18 | Root cause fixed — the single listener slot, below | ☑ |
| 6 | Question bank, exam list, approvals live | NFR 18 | Both screens subscribe to pushed events | ☑ |
| 7 | Bot error message unhelpful | 48 | Internal invariants no longer shown to students | ☑ |
| 8 | Exam Scheduling not updating | NFR 18 | Clock-driven refresh; selection preserved | ☑ |
| 9 | Principal Results tab empty | 54 | Query was missing a column the mapper read | ☑ |
| 10 | Principal report types | 55, 56 | Targets picked by name; execution report reachable | ☑ |
| 11 | Split view and group reports | 55 | Two reports side by side with a comparison summary | ☑ |
| 12 | Serial numbers for all entities | 33, 34, 38, 39 | Encoded identifiers, verified in the database | ☑ |
| 13 | Adjustment reason shown to the student | 39, 40 | Carried through to the published result | ☑ |
| 14 | A course may have several teachers | 15 | Already supported; confirmed in the interface | ☑ |

---

## 2. Added after the list

| Item | Requirement | ✔ |
|---|---|---|
| Schedule an exam written by a colleague | 15, §7.2 | ☑ |
| Subject → course → exam cascade when scheduling | 15 | ☑ |
| Newly approved exams reach the scheduling list live | NFR 18 | ☑ |
| Student's grade appears the moment it is published | NFR 18 | ☑ |
| Student told why her exam time changed, privately | SUC-9 | ☑ |
| Edit a Course Bot source, with versions | 43, 45 | ☑ |
| Separate Principal target/comparison PDF and Excel exports, with comparison summary | 55 | ☑ |
| Keep both versions of a source, or replace | 43 | ☑ |
| Simultaneous source edits detected and recoverable | 45 | ☑ |
| Course Bot management updates live for a colleague | 45, NFR 18 | ☑ |
| Student's bot availability updates live | 46, NFR 18 | ☑ |
| Execution status advances from SCHEDULED to OPEN | SUC-8 | ☑ |
| Submission status follows students during review | 39 | ☑ |
| Study bot seeded so the use case can be shown | 42–50 | ☑ |
| Demo database rebuilt from a generator | — | ☑ |

---

## 3. Encoded identifiers

| | Format | Example |
|---|---|---|
| Question | 2 digit course + 3 digit question | `01042` |
| Exam | 2 digit subject + 2 digit course + 2 digit exam | `010101` |

Both read from the broadest container inwards. Course and subject numbers come
from the external school system (requirement 19), so they are stored rather than
derived. The identifiers are business keys beside the existing surrogate primary
keys, so no foreign key changed.

---

## 4. Defects found by testing

These were not on the list. Each was found by running the system.

**1 · `init.sql` CHECK constraint on an undeclared column**
The schema constrained a column the table did not declare.

**2 · Principal execution query missing `cumulative_extension_minutes`**
The shared row mapper read the column; the Principal's hand-written query was the
only one that did not select it, so the Results tab always failed and appeared
empty.

**3 · One listener slot shared by seven screens**
`Client` held a single `Consumer<ServerEvent>`. Opening a screen displaced the
previous one, and closing any screen cleared the slot for all of them, so after
visiting and leaving a single sub-page nothing received server events at all.

**4 · Published Grades unreachable below the fold**
Only the reviewed exam copy scrolled, sharing the window with a results panel
that grew with it, so on a large screen the adjustment reason could not be
reached.

**5 · Bot timestamps rounding past their own conversation**
`DATETIME(6)` holds microseconds while the clock supplies nanoseconds, and MySQL
rounds rather than truncates, so a stored message came back later than the value
still in memory and rehydration rejected the conversation. Every attempt to ask
the study bot failed.

**6 · Table headings white on near-white**
The shared theme set header text to white but never set a background for the
header row. Every table was affected; one rule corrected all of them.

**7 · Resuming an exam failed on a missing column**
The submission mapper began reading `extension_reason` while the query behind
resume still selected only up to `extra_minutes`, so a student who left an
attempt could not return to it. The same shape as defect 2.

**8 · Execution status never left SCHEDULED**
The transition is derived from the clock, so nothing in the request path ever
wrote it. The entity had `evaluateStatus` for exactly this and it was called from
nowhere. Screens deriving the status disagreed with anything reading the column.

**9 · Grade Review never followed submissions**
The page reacted to the event but skipped the reload whenever a row was
selected, which in practice was always.

**10 · A bot answer arrived a question late**
The server returned the answer and the page discarded it to re-read the whole
history; that second round trip could be dropped by any other refresh.

---

## 5. Lessons for the design discussion

**A definition written twice will drift apart.** Defects 1, 2 and 7 are the same
failure: a constraint or a query duplicating a definition that later changed in
only one place. Note that the duplication in defect 2 was deliberate and correct
— the shared constant carries a teacher authorisation join the Principal must not
have — so the answer is not always to remove duplication, but to test that the
copies still agree. Where such a test existed, as for the bot schema, the drift
surfaced in seconds instead of in the running system.

**Unit tests cannot find defects that live at the boundary.** Defect 5 was
invisible to hundreds of passing tests because every one of them used a fake
database and never round-tripped a timestamp through MySQL. It surfaced only once
realistic seeded data arrived.

**A guard that is too broad stops guarding.** Defect 9 came from a safeguard
protecting typed feedback. It was correct in intent but skipped the whole reload,
so the list never updated at all. Separating the list from the open review kept
the protection and restored the behaviour.

**Errors have an audience.** The study bot showed students an internal timestamp
rule, and the server logged nothing at all, so a friendly message became the only
evidence a fault had occurred. Messages a user reads and messages a developer
reads are different messages, and both are needed.
