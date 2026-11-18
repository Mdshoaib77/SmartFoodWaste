# Database Documentation

Smart Food Waste Sharing and Priority-Based Distribution System
Database: `smart_food_waste` (MySQL 8.x, InnoDB, `utf8mb4`)

This document explains the database design: what each table stores, how the tables
are related, and how to set the database up. The schema itself is in `database.sql`
and the ER diagram is in `docs/ER_DIAGRAM.*`.

---

## 1. Setup

| File | Purpose |
|---|---|
| `database.sql` | Fresh install. Creates the database and all tables. **Drops existing tables first**, so existing data is lost. |
| `migration.sql` | Upgrade an old database without losing data. Safe to run more than once. |

```
cmd /c "mysql -u root -p < database.sql"
```

Connection settings (user, password, pool size) are in `db.properties`.

Notes:
- All timestamps are written by the Java application, not by the MySQL server (`NOW()` is not used).
- The default administrator account is created automatically on first launch.
- Passwords are stored as PBKDF2 hashes, never as plain text.

---

## 2. Tables at a glance

| Table | Purpose |
|---|---|
| `admin` | Administrator accounts |
| `areas` | Standard list of areas (all 64 districts of Bangladesh) |
| `user` | Donors and Receivers/NGOs |
| `food` | Surplus food posted by donors |
| `request` | A receiver/NGO asking for a food item |
| `pickup` | Verification record for an approved request |
| `activity_log` | Timestamped audit trail |
| `message` | Admin messages and automatic system notifications |
| `login_throttle` | Temporary lock after repeated failed logins |

---

## 3. Table details

### 3.1 `admin`
| Column | Type | Notes |
|---|---|---|
| AdminID | INT, PK, auto increment | |
| Username | VARCHAR(50), unique | Login name |
| Password | VARCHAR(255) | PBKDF2 hash |
| Role | VARCHAR(30) | Default `Administrator` |
| MustChangePassword | TINYINT(1) | `1` while the seeded default password is in use |
| CreatedBy | INT, FK to `admin.AdminID` | The admin who created this account (set to NULL if that admin is deleted) |
| CreatedAt | DATETIME | |

### 3.2 `areas`
| Column | Type | Notes |
|---|---|---|
| AreaName | VARCHAR(50), PK | One row per district (64 rows) |

Used by `user.AreaID` and `food.PickupArea`, so every area in the system comes from one standard list.

### 3.3 `user`
| Column | Type | Notes |
|---|---|---|
| UserID | INT, PK | |
| Name | VARCHAR(100) | |
| Contact | VARCHAR(100), unique | Phone or e-mail, used as the login id |
| Password | VARCHAR(255) | PBKDF2 hash |
| Role | ENUM | `Donor` or `Receiver/NGO` |
| AreaID | VARCHAR(50), FK to `areas` | |
| Capacity | INT, nullable | Maximum quantity a Receiver/NGO can take |
| Status | ENUM | `Pending` (default), `Approved`, `Rejected` |
| VerifiedBy | INT, FK to `admin` | Admin who verified the account |
| CreatedAt | DATETIME | |

Rules (CHECK constraints):
- `Capacity` must be greater than 0 when present.
- A `Receiver/NGO` must have a `Capacity`.

Indexes: `idx_user_area (AreaID)`, `idx_user_role_status (Role, Status)`.

### 3.4 `food`
| Column | Type | Notes |
|---|---|---|
| FoodID | INT, PK | |
| FoodName | VARCHAR(100) | |
| Category | VARCHAR(50) | |
| Quantity | INT | Must be greater than 0 |
| PreparationTime | DATETIME | |
| ExpiryTime | DATETIME | |
| PickupArea | VARCHAR(50), FK to `areas` | District where the food is collected. Used for matching donors and receivers |
| Location | VARCHAR(255) | House, road, landmark |
| SafetyInfo | VARCHAR(500), nullable | |
| PriorityLevel | ENUM | `High`, `Medium`, `Low` |
| Status | ENUM | `Available` (default), `Requested`, `Reserved`, `Picked Up`, `Completed`, `Expired` |
| UserID | INT, FK to `user` | The donor who posted it. Deleting the donor deletes the food |
| CreatedAt | DATETIME | |

Indexes: `(Status, ExpiryTime)`, `(UserID)`, `(PickupArea, Status)`. These speed up the "available food, soonest expiry first" and area-matching queries.

### 3.5 `request`
| Column | Type | Notes |
|---|---|---|
| RequestID | INT, PK | |
| FoodID | INT, FK to `food` | |
| UserID | INT, FK to `user` | The receiver/NGO making the request |
| TimeSlot | VARCHAR(50) | Preferred slot, `HH:mm-HH:mm` |
| ApprovedSlot | VARCHAR(50), nullable | Slot fixed or countered by the donor on approval |
| Notes | VARCHAR(500), nullable | |
| Status | ENUM | `Requested` (default), `Reserved`, `Rejected`, `Picked Up`, `Completed`, `Expired`, `Cancelled` |
| IsSuspicious | TINYINT(1) | `1` if flagged as a suspicious request |
| RequestedAt, UpdatedAt | DATETIME | |

Indexes: `(Status, RequestedAt)`, `(UserID)`, `(FoodID)`.

### 3.6 `pickup`
| Column | Type | Notes |
|---|---|---|
| PickupID | INT, PK | |
| RequestID | INT, FK to `request`, unique | Exactly one pickup per request |
| PickupTime | DATETIME, nullable | Set when the code is verified |
| VerificationCode | VARCHAR(10) | Code handed over by the donor, entered by the receiver at pickup |
| Status | ENUM | `Pending` (default), `Picked Up`, `Completed`, `Expired`, `Cancelled` |
| GeneratedAt | DATETIME | |

### 3.7 `activity_log`
| Column | Type | Notes |
|---|---|---|
| LogID | BIGINT, PK | |
| ActorType | ENUM | `Admin`, `User`, `System` |
| ActorID | INT, nullable | Id of the admin or user (NULL for System) |
| Action | VARCHAR(50) | |
| Details | VARCHAR(500), nullable | |
| LoggedAt | DATETIME | Indexed |

`ActorID` is a logical reference only. It has no foreign key, because it points to `admin` or `user` depending on `ActorType`. This keeps old log entries even after an account is removed.

### 3.8 `message`
| Column | Type | Notes |
|---|---|---|
| MessageID | BIGINT, PK | |
| SenderAdminID | INT, FK to `admin`, nullable | NULL means an automatic system notification |
| RecipientUserID | INT, FK to `user` | |
| Subject | VARCHAR(150) | |
| Body | VARCHAR(1000) | |
| CreatedAt | DATETIME | |
| IsRead | TINYINT(1) | `0` unread, `1` read |

Index: `(RecipientUserID, IsRead, CreatedAt)` for fast inbox loading.

### 3.9 `login_throttle`
| Column | Type | Notes |
|---|---|---|
| Kind | ENUM | `User` or `Admin` |
| Identity | VARCHAR(100) | Contact or admin username, trimmed and lower-cased |
| FailedCount | INT | Number of failed attempts |
| LockedUntil | DATETIME, nullable | Account is locked until this time |
| UpdatedAt | DATETIME | |

Primary key is `(Kind, Identity)`. It protects against repeated password guessing.

---

## 4. Relationships

| Relationship | Type | On delete |
|---|---|---|
| `admin` verifies `user` | 1 to many (`user.VerifiedBy`) | SET NULL |
| `admin` creates `admin` | 1 to many (`admin.CreatedBy`) | SET NULL |
| `admin` sends `message` | 1 to many (`message.SenderAdminID`) | SET NULL |
| `areas` to `user` | 1 to many (`user.AreaID`) | none (area names cascade on update) |
| `areas` to `food` | 1 to many (`food.PickupArea`) | none (area names cascade on update) |
| `user` (donor) posts `food` | 1 to many | CASCADE |
| `user` (receiver/NGO) submits `request` | 1 to many | CASCADE |
| `food` receives `request` | 1 to many | CASCADE |
| `request` generates `pickup` | 1 to 1 | CASCADE |
| `user` receives `message` | 1 to many | CASCADE |

---

## 5. How the main flow uses the tables

1. A user registers, so a row is added to `user` with status `Pending`. An admin approves it and sets `VerifiedBy`.
2. An approved donor posts food, so a row is added to `food` with status `Available`.
3. An approved receiver/NGO requests it, so a row is added to `request` and the food becomes `Requested`.
4. The donor approves a slot, so `request` and `food` become `Reserved` and a `pickup` row with a verification code is created.
5. The donor hands over the code and the receiver enters it, so `pickup`, `request` and `food` move to `Picked Up` and then `Completed`.
6. Food past its `ExpiryTime` is marked `Expired`.
7. Every important action is written to `activity_log`, and notifications go into `message`.
