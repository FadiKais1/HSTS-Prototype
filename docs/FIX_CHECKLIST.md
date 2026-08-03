# HSTS — Fix Checklist

Group 7 · branch `final-polish` · 921 tests passing

All items below were implemented, built green, and confirmed working in the
running application.

---

## Requested fixes

| # | Item | Requirement | Where | ✔ |
|---|---|---|---|---|
| 1 | Delete a question from the bank | — | Soft delete: hidden from the bank, kept in exams that use it | ☑ |
| 2 | Editing a question creates a new question | 33, 34 | **Save as New Question** beside the existing versioned update | ☑ |
| 3 | Teacher with courses across subjects | 15, 19 | Subject selector narrows the course list in the Exam Builder | ☑ |
| 4 | Editing an exam creates a new exam | 38, 39 | **Save as New Exam**; original keeps its approval | ☑ |
| 5 | Screens do not update in real time | NFR 18 | Root cause fixed — see below | ☑ |
| 6 | Question bank, exam list and approvals live | NFR 18 | Both screens subscribe to pushed events | ☑ |
| 7 | Bot error message unhelpful | 48 | Internal invariants no longer shown to students | ☑ |
| 8 | Exam Scheduling not updating | NFR 18 | Clock-driven status ticker; selection preserved | ☑ |
| 9 | Principal Results tab empty | 54 | Query was missing a column the mapper read | ☑ |
| 10 | Principal report types | 55, 56 | Targets picked by name; execution report reachable | ☑ |
| 11 | Split view and group reports | 55 | Two reports side by side with a comparison summary | ☑ |
| 12 | Serial numbers for all entities | 33, 34, 38, 39 | Encoded identifiers, verified in the database | ☑ |
| 13 | Adjustment reason shown to the student | 39, 40 | Carried through to the published result | ☑ |
| 14 | A course may have several teachers | 15 | Already supported; confirmed in the interface | ☑ |

---

## Encoded identifiers (requirements 33, 34, 38, 39)

| | Format | Example |
|---|---|---|
| Question | 3-digit question number + 2-digit course number | `00101` |
| Exam | 2-digit exam + 2-digit course + 2-digit subject | `010101` |

Course and subject numbers are supplied externally (requirement 19), so they are
stored rather than derived. Both identifiers are business keys beside the
existing surrogate primary keys, leaving every foreign key untouched.

---

## Defects found by testing

These were not on the original list. Each was found by running the system rather
than by the build, and each is worth recording in the assignment's error column.

**1 · `init.sql` CHECK constraint on an undeclared column**
The schema constrained a column the table did not declare.

**2 · Principal execution query missing `cumulative_extension_minutes`**
The shared row mapper read the column; the Principal's hand-written query was the
only one that did not select it, so the Results tab always failed and appeared
empty. The manager queries were built from a shared constant and were unaffected.

**3 · One listener slot shared by seven screens**
`Client` held a single `Consumer<ServerEvent>`. Opening a screen displaced the
previous one, and closing any screen cleared the slot for all of them, so after
visiting and leaving a single sub-page nothing received server events at all.
Replaced with a subscriber bus where each screen closes only its own
registration.

**4 · Published Grades unreachable below the fold**
Only the reviewed exam copy sat in a scroll pane, sharing the window with a
results panel that grew with it. On a large screen the lower part of the review,
including the adjustment reason, could not be reached.

**5 · Bot timestamps rounding past their own conversation**
Bot times live in `DATETIME(6)` columns holding microseconds, while the clock
supplies nanoseconds and MySQL rounds rather than truncates. A message stored as
`...123457` came back later than the value still held in memory, so rehydration
found a message created after its own conversation and refused it. Every attempt
to ask the study bot failed. Fixed by truncating to the precision the database
stores.

**6 · Table headings white on near-white**
The shared theme set column header text to white but never set a background for
the header row, so JavaFX used its default light grey. Every table in the
application was affected, and one rule corrected all of them.

---

## Two lessons for the design discussion

**A definition written twice will drift apart.** Defects 1 and 2 are the same
failure in different clothes: a constraint and a query each duplicated a
definition that later changed in only one place. Where the manager queries shared
a constant they stayed correct; where the Principal query held its own copy it
did not. Note that the duplication in defect 2 was deliberate and correct — the
shared constant carries a teacher authorisation join the Principal must not have
— so the answer is not always to remove duplication, but to test that the copies
still agree.

**Unit tests cannot find defects that live at the boundary.** Defect 5 was
invisible to 921 passing tests because every one of them used a fake database and
never round-tripped a timestamp through MySQL. It surfaced only once realistic
seeded data arrived, because previous bot conversations were created and used
within a single session and never compared an in-memory value against a stored
one.
