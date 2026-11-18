# REQUIREMENTS TRACEABILITY MATRIX

Source of truth: *Smart Food Waste Sharing and Priority-Based Distribution System - SRS/Proposal (8 June 2026)* and the ER diagram.
Paths are under `src/com/foodwaste/`. "Test area" refers to the groups in `TEST_REPORT.md`.
Status: **Done** = implemented and covered by a passing automated test in `TEST_REPORT.md`; **Done (manual)** = verified by hand/headless run, see "Manual checks" in `TEST_REPORT.md`.

## 1. Functional requirements

| ID | Requirement (short) | Implementation (file -> method) | Test area | Status |
|---|---|---|---|---|
| FR-01 | Register with name, contact, password, role, area; NGO pending until approved | `service/AuthService.register` (hash, area from list, capacity for NGO, Pending/Approved); `service/AuthService.areas`; `dao/UserDAO.insert`; `dao/AreaDAO.findOfficial`; GUI `gui/LoginFrame.doRegister`; console `Main.register`; approval `service/AdminService.verifyReceiver` | Registration | Done |
| FR-02 | Authenticate; role-based access | `service/AuthService.loginUser`, `loginAdmin`, throttling `checkNotLocked`/`failed` + `dao/ThrottleDAO`; role checks `service/Guard.admin/user/anyUser` called by every restricted method of `AdminService`, `FoodService`, `RequestService`, `MessageService`; role-specific frames `gui/AdminFrame`, `DonorFrame`, `ReceiverFrame`; menus `ui/AdminMenu`, `DonorMenu`, `ReceiverMenu` | Authentication, Access Control | Done |
| FR-03 | Donor creates food record (name, category, quantity, preparation time, expiry, location, safety info) | `service/FoodService.postFood` (+ `updateFood`, `deleteFood` only while Available); `dao/FoodDAO.insert/updateAvailable/deleteAvailable`; `gui/DonorFrame.postPage/postsPage/editDialog`; `ui/DonorMenu.postFood/edit/delete` | Priority Calculation, Donor food edit / delete | Done |
| FR-04 | High < 4 h, Medium < 12 h, else Low | `service/PriorityCalculator.calculate`; `dao/FoodDAO.refreshPriorities`; `service/ExpiryService.syncAt` (every minute + on each screen) | Pure logic (3h59m, 4h00m, 11h59m, 12h00m), Priority Calculation | Done |
| FR-05 | Use area + capacity to identify suitable receivers; urgent food to nearby NGOs first | `dao/FoodDAO.findAvailable` (priority, then same PICKUP area (`food.PickupArea`), then expiry; capacity filter); `service/FoodService.availableFor`, `exceedsCapacity`, `nearbyReceivers(donor, foodId)`; `dao/UserDAO.findApprovedReceiversByArea` (area = the food's pickup area when a food is chosen); `service/FoodService.resolveArea`; capacity block in `service/RequestService.submit`; capacity column `user.Capacity`; GUI `ReceiverFrame.browsePage`, `DonorFrame.nearPage`; console `ReceiverMenu` options 1-3, `DonorMenu.nearby` | Receiver Matching (area + capacity) | Done |
| FR-06 | Receiver submits request (food id, preferred slot, notes); donor approves / rejects | `service/RequestService.submit`, `approve(donor, id, slot)`, `reject`, `cancelByReceiver`; slot validation `util/TimeSlot`, `service/Check.slot`; stale auto-cancel `service/ExpiryService.syncAt`; GUI `ReceiverFrame.requestSelected`, `DonorFrame.requestsPage` | Request Processing, stale requests | Done |
| FR-07 | Status sequence Available, Requested, Reserved, Picked Up, Completed | Guarded `UPDATE ... WHERE Status = ?` in `dao/FoodDAO.transition`, `dao/RequestDAO.transition/approve`; used by `RequestService` | Request Processing, Pickup Verification, Concurrency | Done |
| FR-08 | Verification code for pickup; status change with timestamp | `service/RequestService.approve` (6-digit `SecureRandom` code -> `dao/PickupDAO.insert`), `confirmPickup` (constant-time compare, `PickupDAO.markPickedUp` sets PickupTime), `complete`; `dao/ActivityLogDAO` | Pickup Verification | Done |
| FR-09 | Expired before pickup => Expired, included in report | `service/ExpiryService.syncAt` -> `FoodDAO.expireOverdue`, `RequestDAO.expireOverdue`, `PickupDAO.expireOverdue`, notifications; `dao/ReportDAO.expiredQuantity/byPriority/distributionByArea` | Expiry Handling, Reporting | Done |
| FR-10 | Admin monitors donations, requests, approvals, interactions, NGO verification, suspicious requests | `service/AdminService.interactions` (status/area/date/suspicious filters; donor, receiver, food, status, timestamps, slots, pickup time) via `dao/RequestDAO.search`; `pendingReceivers`, `verifyReceiver`, `flagRequest`, `cancelRequest`, `suspiciousRequests`, `recentLogs`, `allUsers`, `allFood`; auto-flag in `RequestService.submit`; GUI `AdminFrame.requestsPage`; console `AdminMenu.monitor` | Administrative monitoring | Done |
| FR-11 | Distribution and waste-reduction reports | `service/AdminService.generateReport` (text), `distributionCsv`, `wasteReductionCsv`, `exportCsv`, `saveReport`; data `dao/ReportDAO` (rescued vs expired quantity, rate, by priority, by area, top donors, top receivers) | Reporting | Done |
| (2.3) | Donor "views administrative messages" | `service/MessageService` (`inbox`, `unreadCount`, `markRead`, `markAllRead`, `sendToUser`, `broadcast`); `service/Notifier` (automatic notifications: request approved/rejected, new request, pickup confirmed, completed, food expired, account approved/rejected, cancelled by admin / receiver / stale, flagged); `gui/MessagesPanel`; console `ui/AccountMenu.inbox` | Administrative messages and notifications | Done |
| (2.2-1) | Administrator role / accounts | `service/AdminService.createAdmin`, `allAdmins`; `service/AuthService.changeAdminPassword`, `changeUserPassword`; default admin seeded with `MustChangePassword`; warning in `gui/AdminFrame` and `ui/AdminMenu.show` | Registration, Authentication | Done |

## 2. Non-functional requirements

| NFR | Implementation | Evidence | Status |
|---|---|---|---|
| Performance: queries ~2 s, >= 5 concurrent users | Indexes in `database.sql`; connection pool `config/DBConnection`; guarded UPDATEs in one transaction (`service/Tx.tx`); row-locked expiry (`FoodDAO.findOverdue ... FOR UPDATE`) | 8 simultaneous requests for one food: exactly 1 wins; 5 simultaneous pickup confirmations: exactly 1; 6 parallel full workflows; 30 threads through the pool; browsing ~2000 foods, report and monitoring query each < 2000 ms (timings in the report) | Done |
| Security: hashed passwords | `util/PasswordUtil` (PBKDF2, random salt) | Authentication / pure-logic tests | Done |
| Security: RBAC | `service/Guard` in the service layer | Access Control (25+ cases: wrong role, forged admin object, rejected user, pending NGO) | Done |
| Security: PreparedStatement only | All SQL through `util/Db` (`prepareStatement`); dynamic filters bind values with `?` | Source scan test: no `createStatement(`; SQL-injection tests | Done |
| Security: brute force | `dao/ThrottleDAO`, `AuthService.failed` | Authentication tests (lock after 5, expiry, admin lock) | Done |
| Usability | Role menus, status chips, `HH:mm` / `yyyy-MM-dd HH:mm` formats (`util/Fmt`, `util/TimeSlot`), clear `ServiceException` messages, GUI dialogs never show stack traces (`gui/Ui.run`) | Validation tests; GUI headless smoke (Done, manual) | Done |
| Reliability | Every multi-step change in one transaction with rollback; notifications in the same transaction | Race and consistency tests (15 approve-vs-cancel rounds) | Done |
| Auditability | `activity_log` (actor, action, details, timestamp) via `dao/ActivityLogDAO`; admin log viewer | Monitoring tests | Done |

## 3. Interfaces, data, architecture, constraints

| Item | Where | Status |
|---|---|---|
| 3.3 Role-specific menus (Administrator, Donor, Receiver/NGO) | GUI frames + console menus | Done |
| 3.3 Database interface via JDBC + PreparedStatement | `config/DBConnection`, `dao/*`, `util/Db` | Done |
| 3.4 User data (id, name, contact, password hash, role, area, status) | `user` table, `model/User` (+ Capacity) | Done |
| 3.4 Food data | `food` table, `model/Food` | Done |
| 3.4 Request data (ids, donor, receiver, slot, notes, status, timestamps) | `request` table, `model/Request` (+ ApprovedSlot, IsSuspicious) | Done |
| 3.4 Pickup data | `pickup` table (code, status, PickupTime, GeneratedAt) | Done |
| 3.4 Administrative data (NGO verification, logs, reports) | `user.VerifiedBy`, `activity_log`, `message`, report services | Done |
| 4 Layered architecture presentation / application / data | `gui` + `ui` / `service` / `dao` + `config` + MySQL | Done |
| 2.5 Offline, local | No network code; JDBC to localhost | Done |
| 2.4 Core Java JDK 8, Swing or console | Compiled with `javac --release 8`; both GUI (`gui/GuiMain`) and console (`Main`) | Done |
| 4.2 Core flow steps 1-7 | login -> `postFood` -> priority -> matching -> `submit/approve` -> code -> expired/completed kept for reports | Done |

## 4. Testing table (Section 5.1)

| Test area | Automated group in `tests/.../TestRunner.java` |
|---|---|
| Registration | `registration()` |
| Authentication | `authentication()` |
| Priority Calculation | `pureLogic()` (boundaries 3h59m, 4h00m, 11h59m, 12h00m) + `priority()` |
| Request Processing | `requestProcessing()`, `staleRequests()` |
| Pickup Verification | `pickupVerification()` |
| Expiry Handling | `expiryHandling()` |
| Access Control | `accessControl()` |
| Reporting | `reporting()` |
| New features | `capacityMatching()`, `messages()`, `donorEditDelete()`, `adminMonitoring()`, `concurrency()`, `security()` |

## 5. Acceptance criteria (5.2)

| Criterion | Evidence | Status |
|---|---|---|
| FR-01..FR-11 implemented and pass their tests | Sections 1 and 4 | Done |
| RBAC prevents unauthorised functions | Access Control tests | Done |
| Priority thresholds correct | Boundary tests | Done |
| Requests progress through the workflow; valid code recorded | Pickup Verification tests | Done |
| Expired uncollected food handled and reported | Expiry Handling + Reporting tests | Done |
| User, food, request, pickup and audit data stored and retrieved correctly | Every DB-backed test reads values back with separate SQL | Done |
| Performance target in lab conditions | Concurrency and performance group | Done (local machine; see report for timings) |

## 6. User documentation (6.3)
Administrator, Donor and Receiver steps are in `README.md` section 6 (demo walkthrough) and map to the sidebar pages of each frame.

## 7. ER entities and relationships

| ER item | Table / FK | Notes |
|---|---|---|
| USER | `user` | Role, Contact, Password, Area (`AreaID` -> `areas`), Capacity, Status |
| ADMIN | `admin` | Username, Password hash, Role, MustChangePassword, CreatedBy |
| FOOD | `food` | PreparationTime, Location, SafetyInfo, PriorityLevel, Status |
| REQUEST | `request` | TimeSlot, ApprovedSlot, Notes, Status, IsSuspicious |
| PICKUP | `pickup` | PickupTime, VerificationCode, Status |
| ACTIVITY_LOG (added) | `activity_log` | audit trail (logical link to actor, no FK by design - survives deletion of users) |
| MESSAGE (added) | `message` | `SenderAdminID` -> admin (NULL = system), `RecipientUserID` -> user |
| AREAS (added) | `areas` | standard list (all 64 districts); `user.AreaID` and `food.PickupArea` -> `areas.AreaName` |
| LOGIN_THROTTLE (added) | `login_throttle` | lock counters |
| ADMIN 1:N USER "Verifies" | `user.VerifiedBy` -> `admin.AdminID` | Done |
| USER 1:N FOOD "Posts" | `food.UserID` | Done |
| USER 1:N REQUEST "Submits" | `request.UserID` | Done |
| FOOD 1:N REQUEST "Receives" | `request.FoodID` | Done |
| REQUEST 1:1 PICKUP "Generates" | `pickup.RequestID` UNIQUE | Done |
