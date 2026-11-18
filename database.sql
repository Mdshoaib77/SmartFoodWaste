-- =====================================================================
--  Smart Food Waste Sharing and Priority-Based Distribution System
--  MySQL 8.x (also runs on MariaDB 10.5+)   |   Run:  mysql -u root -p < database.sql
--  Tables: admin, areas, user, food, request, pickup, activity_log,
--          message, login_throttle   (ER entities + audit/notification support)
--  WARNING: this script DROPS and recreates every table (fresh install).
--           To upgrade an existing database without losing data use migration.sql.
--  All timestamps are written by the Java application (no server NOW()).
--  The default administrator is created automatically on first launch.
-- =====================================================================
CREATE DATABASE IF NOT EXISTS smart_food_waste
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_food_waste;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS login_throttle;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS activity_log;
DROP TABLE IF EXISTS pickup;
DROP TABLE IF EXISTS request;
DROP TABLE IF EXISTS food;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS areas;
DROP TABLE IF EXISTS admin;
SET FOREIGN_KEY_CHECKS = 1;

-- ADMIN ---------------------------------------------------------------
CREATE TABLE admin (
  AdminID            INT AUTO_INCREMENT PRIMARY KEY,
  Username           VARCHAR(50)  NOT NULL UNIQUE,
  Password           VARCHAR(255) NOT NULL,           -- PBKDF2 hash, never plain text
  Role               VARCHAR(30)  NOT NULL DEFAULT 'Administrator',
  MustChangePassword TINYINT(1)   NOT NULL DEFAULT 0, -- 1 while the seeded default password is in use
  CreatedBy          INT NULL,                        -- the administrator who created this account
  CreatedAt          DATETIME NOT NULL,
  CONSTRAINT fk_admin_creator FOREIGN KEY (CreatedBy) REFERENCES admin(AdminID) ON DELETE SET NULL
) ENGINE=InnoDB;

-- AREAS (the single standard list of areas = all 64 districts of Bangladesh) ----
CREATE TABLE areas (
  AreaName VARCHAR(50) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO areas (AreaName) VALUES
  ('Bagerhat'), ('Bandarban'), ('Barguna'), ('Barishal'), ('Bhola'), ('Bogura'),
  ('Brahmanbaria'), ('Chandpur'), ('Chapainawabganj'), ('Chattogram'), ('Chuadanga'),
  ('Cox''s Bazar'), ('Cumilla'), ('Dhaka'), ('Dinajpur'), ('Faridpur'), ('Feni'),
  ('Gaibandha'), ('Gazipur'), ('Gopalganj'), ('Habiganj'), ('Jamalpur'), ('Jashore'),
  ('Jhalokathi'), ('Jhenaidah'), ('Joypurhat'), ('Khagrachhari'), ('Khulna'), ('Kishoreganj'),
  ('Kurigram'), ('Kushtia'), ('Lakshmipur'), ('Lalmonirhat'), ('Madaripur'), ('Magura'),
  ('Manikganj'), ('Meherpur'), ('Moulvibazar'), ('Munshiganj'), ('Mymensingh'), ('Naogaon'),
  ('Narail'), ('Narayanganj'), ('Narsingdi'), ('Natore'), ('Netrokona'), ('Nilphamari'),
  ('Noakhali'), ('Pabna'), ('Panchagarh'), ('Patuakhali'), ('Pirojpur'), ('Rajbari'),
  ('Rajshahi'), ('Rangamati'), ('Rangpur'), ('Satkhira'), ('Shariatpur'), ('Sherpur'),
  ('Sirajganj'), ('Sunamganj'), ('Sylhet'), ('Tangail'), ('Thakurgaon');

-- USER (Donor / Receiver-NGO) -----------------------------------------
CREATE TABLE `user` (
  UserID      INT AUTO_INCREMENT PRIMARY KEY,
  Name        VARCHAR(100) NOT NULL,
  Contact     VARCHAR(100) NOT NULL UNIQUE,        -- phone/e-mail, used as login id
  Password    VARCHAR(255) NOT NULL,               -- PBKDF2 hash
  Role        ENUM('Donor','Receiver/NGO') NOT NULL,
  AreaID      VARCHAR(50)  NOT NULL,               -- one of areas.AreaName
  Capacity    INT NULL,                            -- Receiver/NGO: max quantity it can take (FR-05)
  Status      ENUM('Pending','Approved','Rejected') NOT NULL DEFAULT 'Pending',
  VerifiedBy  INT NULL,                            -- ER relationship: ADMIN Verifies USER
  CreatedAt   DATETIME NOT NULL,
  CONSTRAINT fk_user_admin FOREIGN KEY (VerifiedBy) REFERENCES admin(AdminID) ON DELETE SET NULL,
  CONSTRAINT fk_user_area  FOREIGN KEY (AreaID) REFERENCES areas(AreaName) ON UPDATE CASCADE,
  CONSTRAINT chk_user_capacity CHECK (Capacity IS NULL OR Capacity > 0),
  CONSTRAINT chk_user_receiver_capacity CHECK (Role <> 'Receiver/NGO' OR Capacity IS NOT NULL),
  INDEX idx_user_area (AreaID),
  INDEX idx_user_role_status (Role, Status)
) ENGINE=InnoDB;

-- FOOD (posted by a Donor) --------------------------------------------
CREATE TABLE food (
  FoodID           INT AUTO_INCREMENT PRIMARY KEY,
  FoodName         VARCHAR(100) NOT NULL,
  Category         VARCHAR(50)  NOT NULL,
  Quantity         INT NOT NULL,
  PreparationTime  DATETIME NOT NULL,
  ExpiryTime       DATETIME NOT NULL,
  PickupArea       VARCHAR(50)  NOT NULL,          -- district where the food is collected (used for matching, FR-05)
  Location         VARCHAR(255) NOT NULL,          -- address detail: house / road / landmark
  SafetyInfo       VARCHAR(500) NULL,
  PriorityLevel    ENUM('High','Medium','Low') NOT NULL,
  Status           ENUM('Available','Requested','Reserved','Picked Up','Completed','Expired')
                   NOT NULL DEFAULT 'Available',
  UserID           INT NOT NULL,                   -- ER relationship: USER Posts FOOD (donor)
  CreatedAt        DATETIME NOT NULL,
  CONSTRAINT fk_food_user FOREIGN KEY (UserID) REFERENCES `user`(UserID) ON DELETE CASCADE,
  CONSTRAINT fk_food_area FOREIGN KEY (PickupArea) REFERENCES areas(AreaName) ON UPDATE CASCADE,
  CONSTRAINT chk_food_quantity CHECK (Quantity > 0),
  INDEX idx_food_status_expiry (Status, ExpiryTime),
  INDEX idx_food_user (UserID),
  INDEX idx_food_pickup_area (PickupArea, Status)
) ENGINE=InnoDB;

-- REQUEST (Receiver/NGO asks for a FOOD item) -------------------------
CREATE TABLE request (
  RequestID     INT AUTO_INCREMENT PRIMARY KEY,
  FoodID        INT NOT NULL,                      -- FOOD Receives REQUEST
  UserID        INT NOT NULL,                      -- USER Submits REQUEST (receiver/NGO)
  TimeSlot      VARCHAR(50)  NOT NULL,             -- preferred slot, HH:mm-HH:mm
  ApprovedSlot  VARCHAR(50)  NULL,                 -- slot fixed/countered by the donor on approval
  Notes         VARCHAR(500) NULL,
  Status        ENUM('Requested','Reserved','Rejected','Picked Up','Completed','Expired','Cancelled')
                NOT NULL DEFAULT 'Requested',
  IsSuspicious  TINYINT(1) NOT NULL DEFAULT 0,     -- FR-10 suspicious-request management
  RequestedAt   DATETIME NOT NULL,
  UpdatedAt     DATETIME NOT NULL,
  CONSTRAINT fk_request_food FOREIGN KEY (FoodID) REFERENCES food(FoodID)   ON DELETE CASCADE,
  CONSTRAINT fk_request_user FOREIGN KEY (UserID) REFERENCES `user`(UserID) ON DELETE CASCADE,
  INDEX idx_request_status (Status, RequestedAt),
  INDEX idx_request_user (UserID),
  INDEX idx_request_food (FoodID)
) ENGINE=InnoDB;

-- PICKUP (one verification record per approved request) ---------------
CREATE TABLE pickup (
  PickupID          INT AUTO_INCREMENT PRIMARY KEY,
  RequestID         INT NOT NULL UNIQUE,           -- REQUEST Generates PICKUP (1:1)
  PickupTime        DATETIME NULL,                 -- set when the code is verified
  VerificationCode  VARCHAR(10) NOT NULL,
  Status            ENUM('Pending','Picked Up','Completed','Expired','Cancelled')
                    NOT NULL DEFAULT 'Pending',
  GeneratedAt       DATETIME NOT NULL,
  CONSTRAINT fk_pickup_request FOREIGN KEY (RequestID) REFERENCES request(RequestID) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ACTIVITY_LOG (timestamped audit trail) ------------------------------
CREATE TABLE activity_log (
  LogID      BIGINT AUTO_INCREMENT PRIMARY KEY,
  ActorType  ENUM('Admin','User','System') NOT NULL,
  ActorID    INT NULL,
  Action     VARCHAR(50)  NOT NULL,
  Details    VARCHAR(500) NULL,
  LoggedAt   DATETIME NOT NULL,
  INDEX idx_log_time (LoggedAt)
) ENGINE=InnoDB;

-- MESSAGE (administrator messages + automatic system notifications) ---
CREATE TABLE message (
  MessageID        BIGINT AUTO_INCREMENT PRIMARY KEY,
  SenderAdminID    INT NULL,                       -- NULL = automatic system notification
  RecipientUserID  INT NOT NULL,
  Subject          VARCHAR(150) NOT NULL,
  Body             VARCHAR(1000) NOT NULL,
  CreatedAt        DATETIME NOT NULL,
  IsRead           TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_message_admin FOREIGN KEY (SenderAdminID)   REFERENCES admin(AdminID)  ON DELETE SET NULL,
  CONSTRAINT fk_message_user  FOREIGN KEY (RecipientUserID) REFERENCES `user`(UserID)  ON DELETE CASCADE,
  INDEX idx_message_recipient (RecipientUserID, IsRead, CreatedAt)
) ENGINE=InnoDB;

-- LOGIN_THROTTLE (temporary lock after repeated failed logins) --------
CREATE TABLE login_throttle (
  Kind         ENUM('User','Admin') NOT NULL,
  Identity     VARCHAR(100) NOT NULL,              -- contact / admin username, trimmed + lower-cased
  FailedCount  INT NOT NULL DEFAULT 0,
  LockedUntil  DATETIME NULL,
  UpdatedAt    DATETIME NOT NULL,
  PRIMARY KEY (Kind, Identity)
) ENGINE=InnoDB;

-- =====================================================================
--  DATABASE NOTES AND VERIFICATION QUERIES
--  Everything below is commented out, so running this script creates
--  exactly the same database as before. To use a query, copy it and run
--  it in MySQL Workbench or the mysql client AFTER the script has finished.
--  See docs/DATABASE.md for the full table and relationship description.
-- =====================================================================

-- Table creation order (parents before children, because of foreign keys):
--   1. admin            2. areas            3. user
--   4. food             5. request          6. pickup
--   7. activity_log     8. message          9. login_throttle
--
-- Drop order at the top of this file is the reverse, for the same reason.

-- 1) List all tables (expected: 9 tables)
-- SHOW TABLES;

-- 2) Show the structure of any table
-- DESCRIBE `user`;
-- DESCRIBE food;
-- DESCRIBE request;

-- 3) Check that all 64 districts were inserted (expected: 64)
-- SELECT COUNT(*) AS total_areas FROM areas;

-- 4) List the foreign keys of the database
-- SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
--   FROM information_schema.KEY_COLUMN_USAGE
--  WHERE TABLE_SCHEMA = 'smart_food_waste' AND REFERENCED_TABLE_NAME IS NOT NULL
--  ORDER BY TABLE_NAME;

-- 5) Check the indexes of a table
-- SHOW INDEX FROM food;

-- 6) Count rows in every table
-- SELECT 'admin' AS tbl, COUNT(*) AS total_rows FROM admin
-- UNION ALL SELECT 'areas', COUNT(*) FROM areas
-- UNION ALL SELECT 'user', COUNT(*) FROM `user`
-- UNION ALL SELECT 'food', COUNT(*) FROM food
-- UNION ALL SELECT 'request', COUNT(*) FROM request
-- UNION ALL SELECT 'pickup', COUNT(*) FROM pickup
-- UNION ALL SELECT 'activity_log', COUNT(*) FROM activity_log
-- UNION ALL SELECT 'message', COUNT(*) FROM message
-- UNION ALL SELECT 'login_throttle', COUNT(*) FROM login_throttle;

-- 7) Example report: available food, soonest expiry first
-- SELECT FoodID, FoodName, Quantity, PickupArea, PriorityLevel, ExpiryTime
--   FROM food
--  WHERE Status = 'Available'
--  ORDER BY ExpiryTime ASC;

-- 8) Example report: number of requests per status
-- SELECT Status, COUNT(*) AS total FROM request GROUP BY Status;