# CHANGELOG - changes since the submitted version

## Premium UI update (presentation only - no logic changed)
| Change | Files |
|---|---|
| One design system: green/amber palette, Inter font (bundled, works offline), 4/8 px spacing, rounded cards with soft shadows, button variants with hover / focus / loading states, rounded inputs with focus ring, styled drop-downs, spinners, check boxes and scroll bars, status/priority pills, empty states, vector icons. | `gui/Theme`, `gui/Skin`, `gui/Icons`, `gui/ObjTable`, `assets/fonts/` |
| **One reusable modal component** replaces every `JOptionPane`: success / error / warning / info / confirm / delete-confirm, small forms, text prompt, pickup-code display, plus non-blocking toasts and a delayed busy spinner. Blurred backdrop, animated icon, focus kept inside, Esc cancels, Enter confirms (never on a delete). | `gui/Modal`, `gui/Ui` |
| Screens restyled: sign-in / register, header with user chip, icon sidebar with unread badge, dashboards with responsive stat cards and short step guides, table pages with search, wrapping toolbars and record counter. Filler text shortened. | `gui/LoginFrame`, `gui/BaseFrame`, `gui/DonorFrame`, `gui/ReceiverFrame`, `gui/AdminFrame`, `gui/PasswordPanel`, `gui/GuiMain` |
| Untouched: `dao/`, `model/`, `service/`, `util/`, `config/`, `ui/` (console), `database.sql`, `migration.sql`, `db.properties`, `tests/`. |  |

## Pickup Area update
| Change | Files |
|---|---|
| Food now has its own **Pickup Area** (district drop-down, default = donor's area); `Location` is now only the address detail. Matching (receiver browse, "my area only", nearby receivers, report by area, admin area filter) uses the food's pickup area, not the donor's registered area. | `database.sql`, `migration.sql` (step 8), `model/Food`, `model/Request`, `dao/FoodDAO`, `dao/RequestDAO`, `dao/ReportDAO`, `service/FoodService` (`areas()`, `resolveArea`, new `postFood`/`updateFood` argument), `service/AdminService` (report titles) |
| Standard area list = all **64 districts** (old sub-areas of Bogura folded into `Bogura` by the migration). | `database.sql`, `migration.sql` |
| GUI: pickup-area drop-down in *Post Surplus Food* and in the edit dialog; "Pickup area" columns for donor, receiver and admin tables. Console: numbered pickup-area choice when posting / editing. | `gui/DonorFrame`, `gui/ReceiverFrame`, `gui/AdminFrame`, `ui/DonorMenu`, `ui/Views`, `ui/ConsoleIO.chooseArea` |
| 21 new automated checks (area list, default area, other-area posting, per-area visibility, nearby receivers, edit, unknown area rejected, admin filter, report). | `tests/.../TestRunner.java`, `Env.java` |
| ER diagram: `FOOD.PickupArea` + relationship `AREAS 1-N FOOD`. | `docs/ER_DIAGRAM.*` |

## Added files
| File | Purpose |
|---|---|
| `migration.sql` | Upgrade an existing database without losing data (idempotent). |
| `run-tests.bat`, `run-tests.sh` | Build + run the test suite on `smart_food_waste_test`. |
| `tests/com/foodwaste/tests/TestRunner.java`, `Env.java`, `T.java` | Test runner (plain Java, no libraries), DB set-up helpers, result recorder that writes `TEST_REPORT.md`. |
| `TEST_REPORT.md`, `REQUIREMENTS_TRACEABILITY.md`, `CHANGELOG.md`, `README.md` | Documentation (README rewritten). |
| `docs/ER_DIAGRAM.png / .svg / .mmd / .dot` | Corrected ER diagram (Mermaid source + rendered images). |
| `src/.../dao/AreaDAO.java` | Standard area list, case-insensitive lookup. |
| `src/.../dao/MessageDAO.java` | Inbox, unread count, mark read, admin sent list. |
| `src/.../dao/ThrottleDAO.java` | Failed-login counters and temporary locks. |
| `src/.../model/Message.java` | Message / notification model. |
| `src/.../service/Check.java` | Shared validation (text, password, capacity, time slot, area key). |
| `src/.../service/Guard.java` | Service-layer role enforcement (re-loads caller from DB). |
| `src/.../service/Notifier.java` | Writes automatic system notifications inside the caller's transaction. |
| `src/.../service/MessageService.java` | Inbox + admin send / broadcast. |
| `src/.../util/TimeSlot.java` | `HH:mm-HH:mm` validation. |
| `src/.../util/Csv.java` | CSV writer (quotes, formula-injection guard). |
| `src/.../gui/MessagesPanel.java`, `PasswordPanel.java` | Inbox page and change-password page. |
| `src/.../ui/AccountMenu.java` | Console inbox + change password. |

## Changed files
| File | What changed |
|---|---|
| `database.sql` | Rewritten: new tables `areas`, `message`, `login_throttle`; new columns `user.Capacity`, `request.ApprovedSlot`, `admin.MustChangePassword/CreatedBy/CreatedAt`; FKs, CHECK constraints, indexes. |
| `build.bat`, `build.sh` | `--release 8`, `-sourcepath` (paths with spaces / brackets work), fallback if `--release 8` is refused. |
| `config/DBConnection.java` | Fixed-size connection pool (returns connections on `close()`), `configure()` for tests, `db.pool.size`. |
| `util/Constants.java` | New constants: stale hours, lock rules, password length, audiences, flag threshold. |
| `model/User.java`, `Admin.java`, `Request.java` | Capacity; must-change-password / creator; approved slot, areas, pickup time/status. |
| `dao/UserDAO.java` | Capacity, area + capacity matching query (`findApprovedReceiversByArea`), role/status lists, password update. |
| `dao/AdminDAO.java` | List, count, create with flags, password update. |
| `dao/FoodDAO.java` | Matching order + capacity filter, guarded update/delete, overdue list (row-locked). |
| `dao/RequestDAO.java` | Joined display fields, `search` (status/area/date/suspicious), stale list, guarded `approve`. |
| `dao/ReportDAO.java` | Rescued vs expired quantities by priority and area. |
| `service/AuthService.java` | Capacity + area validation, login throttling, change password (user/admin), default admin flagged. |
| `service/FoodService.java` | Capacity-aware browse and nearby receivers, edit, delete, role guard. |
| `service/RequestService.java` | Capacity block, slot validation, counter slot, receiver cancel, notifications, role guard. |
| `service/AdminService.java` | Every method takes the admin and checks it; monitoring filters; create admin; notifications; text + CSV reports. |
| `service/ExpiryService.java` | Expiry notifications; stale-request auto-cancel. |
| `service/PriorityCalculator.java` | Exact (to the second) boundary comparison. |
| `Main.java`, `ui/*` | Console: area list, capacity, inbox, change password, edit/delete, cancel, slot validation, admin messages, monitoring filters, CSV, admin accounts. |
| `gui/LoginFrame.java` | Area drop-down, capacity field, default-password hint removed. |
| `gui/DonorFrame.java`, `ReceiverFrame.java` | Edit/delete, counter slot, capacity column + filter, cancel, inbox with unread badge, change password, nearby receivers by food. |
| `gui/AdminFrame.java` | Interactions page with filters, send message / broadcast, administrators page, change password, CSV export, default-password warning. |
| `gui/BaseFrame.java`, `ObjTable.java`, `Ui.java` | Badge hook, new status chips, slot prompt. |

## Unchanged
`db.properties`, `.vscode/launch.json`, `run.bat/.sh`, `run-gui.bat/.sh`, the MySQL connector jar, `util/Db.java`, `PasswordUtil`, `TextTable`, `Fmt`, `Tx`, `ServiceException`, `Theme`, `GuiMain`, DAO/model classes not listed above.
`out/` is freshly compiled (`javac --release 8`).
