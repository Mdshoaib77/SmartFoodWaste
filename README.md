# Smart Food Waste Sharing and Priority-Based Distribution System

Core Java (JDK 8 compatible) + Swing GUI + Console + MySQL 8 (JDBC). Works offline on Windows.
University project - Pundra University of Science and Technology (CSE).

---

> **Interface note:** the desktop screens use one shared look (`gui/Theme`, `gui/Skin`) and one popup component (`gui/Modal`).
> The Inter font in `assets/fonts/` is loaded automatically; run the app from this folder (as the scripts do). If the font files are missing, a system font is used.

## 1. What you need (prerequisites)

| Thing | Version | Check with |
|---|---|---|
| JDK | 8 or newer (JDK 17 / 21 / 25 all fine) | `javac -version` |
| MySQL Server | 8.x (running on port 3306) | MySQL Workbench or `mysql --version` |
| MySQL Connector/J | already inside `lib/` | `dir lib` |

## 2. Create the database (one time)

The file `database.sql` creates the database `smart_food_waste` and every table (**it drops old tables first - fresh install only**).

**Command Prompt (cmd):**
```
mysql -u root -p < database.sql
```

**PowerShell** - the `<` sign does not work in PowerShell. Use this instead:
```
cmd /c "mysql -u root -p < database.sql"
```
If `mysql` is not found, use the full path, for example:
```
cmd /c ""C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < database.sql"
```
**MySQL Workbench:** File > Open SQL Script > `database.sql` > press the lightning button.

### Already have the old database with data?
Do **not** run `database.sql` (it deletes data). Run the upgrade script instead - it keeps all your rows:
```
cmd /c "mysql -u root -p < migration.sql"
```
It can be run more than once safely. Existing receivers get a default capacity of 100 (change it later in the database if needed), old free-text areas become part of the standard area list, all 64 districts are added (the old sub-areas Bogura Sadar / Gabtali / Shajahanpur / Shibganj become **Bogura**), every existing food gets a **Pickup Area** (= its donor's area), and the old admin gets the "please change password" warning.

## 3. Edit `db.properties`
Open `db.properties` and set your MySQL password:
```
db.url=jdbc:mysql://localhost:3306/smart_food_waste?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
db.user=root
db.password=YOUR_PASSWORD
```
Optional: `db.pool.size=10` (number of pooled connections, 2-100).

## 4. Build and run

### PowerShell / Command Prompt (Windows)
Open the terminal **inside the project folder** (the folder that contains `src`, `lib`, `build.bat`). Folder names with spaces or `( )` are fine.
```
.\build.bat          # compile (Java 8 compatible)
.\run-gui.bat        # Swing GUI
.\run.bat            # console version
```
Linux / macOS: `./build.sh`, `./run-gui.sh`, `./run.sh`.

### VS Code
1. Open the project folder (File > Open Folder).
2. Install the "Extension Pack for Java".
3. Press **F5** and choose **Run GUI (Swing)** or **Run Console** (`.vscode/launch.json` is included).

## 5. Default administrator
Username `admin`, password `admin123`.
The login screen no longer shows this. After the first administrator login you get a warning - **go to Change Password and change it**. You can also create more administrators (Administrators page).

## 6. Demo walkthrough (follow this for the presentation)

1. **Admin:** run `run-gui.bat`, tick *Sign in as Administrator*, login `admin` / `admin123`, change the password (sidebar > Change Password).
2. **Register a Donor:** Logout > Register tab > role **Donor**, choose your district (for example *Bogura*; the list has all 64 districts of Bangladesh).
3. **Register a Receiver/NGO:** role **Receiver/NGO**, same area, **Capacity** = for example `50` (the biggest quantity this NGO can take). The account is *Pending*.
4. **Admin approves the NGO:** Admin > *Verify NGOs* > select > **Approve**. The NGO gets a notification in its inbox.
5. **Donor posts food:** Donor > *Post Surplus Food* (quantity 20, expiry `+3h` => priority **High**). Choose the **Pickup area** (district where the food is collected; default = your own district) and type the **address detail** (house / road / landmark). The message tells how many NGOs in that pickup area have enough capacity.
6. **Receiver browses:** Receiver > *Browse Food*: urgent food whose **pickup area** is the receiver's own district is first (tick "my area only" to hide the rest); food larger than the capacity shows **TOO LARGE** and cannot be requested.
7. **Receiver requests:** select the food > *Request selected food* > time slot like `16:00-18:00`.
8. **Donor approves:** Donor > *Incoming Requests* > **Approve** > confirm or change the slot (counter-proposal) > a **6-digit code** appears. The receiver sees the approved slot in *My Requests* and a message in *Messages*.
9. **Verify pickup:** Receiver > *My Requests* > **Confirm pickup (enter code)**. A wrong code is refused; the right code makes it *Picked Up*.
10. **Complete:** Donor > *Incoming Requests* > **Mark completed**.
11. **Monitor:** Admin > *Interactions* (filter by status / area / date), *Activity Log*, flag / cancel a request.
12. **Reports:** Admin > *Reports* > **Generate report**, **Save to file**, **Export CSV reports** (files go to the `reports` folder).
13. Other things to show: Admin > *Send Message* (to one user or broadcast to all donors), receiver **Cancel request**, donor **Edit / Delete** of an Available post.

## 7. How the rules work (decisions)

| Rule | Behaviour |
|---|---|
| Priority | High: less than 4 h to expiry, Medium: less than 12 h, otherwise Low. Re-checked every minute and on every screen open. |
| Capacity (FR-05) | Receiver/NGO must give a capacity > 0. A request for food larger than the capacity is **blocked** (not only warned) - with a clear message; the browse table marks it `TOO LARGE`, and a checkbox hides such food. Donor's *Nearby Receivers* shows area + capacity, and for a selected food only receivers that can handle its quantity. |
| Areas | One list in the `areas` table = **all 64 districts of Bangladesh**. Registration and food posting use a drop-down / numbered list. Compared trimmed and case-insensitively. A user's area is the *home* district; **matching uses the food's `PickupArea`** (a donor may offer food in another district than the one registered in), while `Location` is only the free-text address detail. Reports and the admin area filter also use the pickup area. Add an area: `INSERT INTO areas (AreaName) VALUES ('Your Area');` |
| Stale requests | A request still *Requested* after `STALE_REQUEST_HOURS` (2, in `util/Constants.java`) is cancelled automatically, food returns to Available, both sides are notified. |
| Edit / delete food | Only the owner, only while the status is Available. Deleting also removes old rejected/cancelled requests of that food. |
| Login throttling | 5 wrong passwords => 5 minutes lock (`MAX_FAILED_LOGINS`, `LOCK_MINUTES`), logged as `LOGIN_FAILED` / `LOGIN_LOCKED`. |
| Roles | Every service method re-checks the caller in the database (`Guard`), so the GUI cannot be bypassed. |
| Suspicious | A receiver who already holds 3 open requests gets the 4th flagged automatically; admins can flag/unflag and cancel. |

## 8. Tests
```
.\run-tests.bat        (Linux/macOS: ./run-tests.sh)
```
Plain Java, no libraries. It builds a **separate database `smart_food_waste_test`** from `database.sql` (your real data is never touched), runs 300+ checks (8 proposal test areas + capacity, messages, cancel, stale, edit/delete, monitoring, CSV, 8-thread races, pool, performance, SQL injection) and rewrites `TEST_REPORT.md`. The MySQL user in `db.properties` must be allowed to create databases (root is).

## 9. Folder layout
```
src/            application (config, dao, gui, model, service, ui, util)
tests/          test runner (no external libraries)
lib/            MySQL Connector/J
assets/fonts/   Inter font files (UI typography, loaded automatically)
out/            compiled classes (rebuild with build.bat)
database.sql    fresh install      migration.sql   upgrade of an existing database
db.properties   connection settings
docs/           ER diagram (PNG, SVG, Mermaid, Graphviz source)
reports/        created when you save text/CSV reports
README.md  TEST_REPORT.md  REQUIREMENTS_TRACEABILITY.md  CHANGELOG.md
```

## 10. Troubleshooting
* **"Cannot connect to MySQL"** - MySQL service not running, wrong password in `db.properties`, or `database.sql` not run yet.
* **"Unknown column 'Capacity'"** - you are on the old database: run `migration.sql`.
* **PowerShell says `<` is reserved** - use the `cmd /c "..."` form from section 2.
* **Account locked** - wait 5 minutes, or run `DELETE FROM login_throttle;` in MySQL.
* Passwords are stored hashed (PBKDF2). A forgotten admin password cannot be read back; create a fresh database or ask another administrator to create a new admin.
