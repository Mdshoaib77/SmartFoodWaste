## Manual / environment checks (not part of the automated table)

These were done once by hand in the build environment (Linux, JDK 21, MySQL 8.0.46). They are **not** repeated by `run-tests`, so `TEST_REPORT.md` regenerates without them - they are kept here.

| Check | Result |
|---|---|
| `database.sql` on a fresh MySQL 8.0.46 (`mysql < database.sql`) | Ran with no error. Also ran on MariaDB 10.11 earlier in the session. |
| `migration.sql` on a database created with the **original** `database.sql` (3 users, 1 food, 1 request, 1 admin row inserted first), run **twice** | No errors; rows kept; `' Bogura Sadar '` became `Bogura` (old sub-areas are folded into the district; every food got its donor's area as `PickupArea`); receivers received capacity 100; 15 seeded areas + the old free-text area `Mirpur-10` were added to `areas`; new tables created; second run changed nothing. |
| Compile with `javac --release 8` (JDK 21), 74 classes, zero errors; `-sourcepath` build gives the same 74 classes as compiling every file | Pass. (Compiling with `--release 8` checks that only Java 8 APIs are used. It was **not** run on a real JDK 8 or JDK 25 runtime.) |
| Console edition, scripted input (register donor + NGO, bad capacity re-prompt, admin login with default-password warning, approve NGO, monitoring filter prompts) | Worked as designed. |
| Swing GUI under a virtual display (Xvfb): Login, Admin (7 pages), Donor (7 pages), Receiver (5 pages) opened with live data, no exception; screenshots reviewed (login, admin interactions, receiver browse with `TOO LARGE` chip, unread-message badge) | Pass. One layout bug (filter buttons cut off) and one truncated hint text were found and fixed before packaging. |
| `grep` for `createStatement`, `TODO`, `FIXME` in `src/` | None found. The only hard-coded password text is the seed password of the default administrator in `AuthService` (required: `admin / admin123`). |

**Not tested:** Windows itself (PowerShell, `.bat` scripts, VS Code launch), JDK 8 / JDK 25 runtimes, real mouse/keyboard use of every dialog (edit-food dialog, slot prompt, send-message form were compiled and reviewed but not clicked through by automation), more than one machine, MySQL versions other than 8.0.46.
