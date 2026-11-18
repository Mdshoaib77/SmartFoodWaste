-- =====================================================================
--  migration.sql - upgrades an EXISTING smart_food_waste database to the
--  final schema WITHOUT dropping any data.   Run:  mysql -u root -p < migration.sql
--  Safe to run more than once (every step checks information_schema first).
--  Take a backup first:  mysqldump -u root -p smart_food_waste > backup.sql
-- =====================================================================
USE smart_food_waste;

-- helper: runs one DDL statement only when the condition query returns 0 -----------------
DROP PROCEDURE IF EXISTS sfw_exec_if;
DELIMITER $$
CREATE PROCEDURE sfw_exec_if(IN cond_sql VARCHAR(1000), IN ddl_sql VARCHAR(1000))
BEGIN
  SET @c = 0;
  SET @q = CONCAT('SELECT (', cond_sql, ') INTO @c');
  PREPARE s1 FROM @q; EXECUTE s1; DEALLOCATE PREPARE s1;
  IF @c = 0 THEN
    SET @d = ddl_sql;
    PREPARE s2 FROM @d; EXECUTE s2; DEALLOCATE PREPARE s2;
  END IF;
END$$
DELIMITER ;

-- 1. ADMIN: new columns ---------------------------------------------------------------------
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin' AND COLUMN_NAME='MustChangePassword'",
 "ALTER TABLE admin ADD COLUMN MustChangePassword TINYINT(1) NOT NULL DEFAULT 0");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin' AND COLUMN_NAME='CreatedBy'",
 "ALTER TABLE admin ADD COLUMN CreatedBy INT NULL");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin' AND COLUMN_NAME='CreatedAt'",
 "ALTER TABLE admin ADD COLUMN CreatedAt DATETIME NULL");
UPDATE admin SET CreatedAt = CURRENT_TIMESTAMP WHERE CreatedAt IS NULL;
ALTER TABLE admin MODIFY CreatedAt DATETIME NOT NULL;
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='admin' AND CONSTRAINT_NAME='fk_admin_creator'",
 "ALTER TABLE admin ADD CONSTRAINT fk_admin_creator FOREIGN KEY (CreatedBy) REFERENCES admin(AdminID) ON DELETE SET NULL");

-- 2. AREAS: standard list, seeded + every area already used by an existing user ---------------
CREATE TABLE IF NOT EXISTS areas (AreaName VARCHAR(50) NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO areas (AreaName) VALUES
  ('Bogura Sadar'), ('Shajahanpur'), ('Gabtali'), ('Sherpur'), ('Shibganj'),
  ('Rajshahi'), ('Naogaon'), ('Pabna'), ('Rangpur'), ('Dhaka'),
  ('Chattogram'), ('Khulna'), ('Sylhet'), ('Barishal'), ('Mymensingh');
-- trim stray spaces, then keep each old free-text area as an official area (nothing is lost)
UPDATE `user` SET AreaID = TRIM(AreaID);
INSERT IGNORE INTO areas (AreaName) SELECT DISTINCT AreaID FROM `user`;
-- make existing users use the canonical spelling of an area that differs only by upper/lower case
UPDATE `user` u JOIN areas a ON LOWER(a.AreaName) = LOWER(u.AreaID) SET u.AreaID = a.AreaName;
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='user' AND CONSTRAINT_NAME='fk_user_area'",
 "ALTER TABLE `user` ADD CONSTRAINT fk_user_area FOREIGN KEY (AreaID) REFERENCES areas(AreaName) ON UPDATE CASCADE");

-- 3. USER: capacity (FR-05). Existing receivers get a default capacity of 100 - edit if needed -
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user' AND COLUMN_NAME='Capacity'",
 "ALTER TABLE `user` ADD COLUMN Capacity INT NULL AFTER AreaID");
UPDATE `user` SET Capacity = 100 WHERE Role = 'Receiver/NGO' AND (Capacity IS NULL OR Capacity <= 0);
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='chk_user_capacity'",
 "ALTER TABLE `user` ADD CONSTRAINT chk_user_capacity CHECK (Capacity IS NULL OR Capacity > 0)");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME='chk_user_receiver_capacity'",
 "ALTER TABLE `user` ADD CONSTRAINT chk_user_receiver_capacity CHECK (Role <> 'Receiver/NGO' OR Capacity IS NOT NULL)");

-- 4. REQUEST: approved (countered) time slot + indexes -----------------------------------------
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='request' AND COLUMN_NAME='ApprovedSlot'",
 "ALTER TABLE request ADD COLUMN ApprovedSlot VARCHAR(50) NULL AFTER TimeSlot");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='request' AND INDEX_NAME='idx_request_user'",
 "ALTER TABLE request ADD INDEX idx_request_user (UserID)");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='request' AND INDEX_NAME='idx_request_food'",
 "ALTER TABLE request ADD INDEX idx_request_food (FoodID)");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='request' AND INDEX_NAME='idx_request_status' AND COLUMN_NAME='RequestedAt'",
 "ALTER TABLE request DROP INDEX idx_request_status, ADD INDEX idx_request_status (Status, RequestedAt)");

-- 5. FOOD: index on donor ------------------------------------------------------------------------
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='food' AND INDEX_NAME='idx_food_user'",
 "ALTER TABLE food ADD INDEX idx_food_user (UserID)");

-- 6. MESSAGE + LOGIN_THROTTLE (new tables) ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
  MessageID        BIGINT AUTO_INCREMENT PRIMARY KEY,
  SenderAdminID    INT NULL,
  RecipientUserID  INT NOT NULL,
  Subject          VARCHAR(150) NOT NULL,
  Body             VARCHAR(1000) NOT NULL,
  CreatedAt        DATETIME NOT NULL,
  IsRead           TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_message_admin FOREIGN KEY (SenderAdminID)   REFERENCES admin(AdminID)  ON DELETE SET NULL,
  CONSTRAINT fk_message_user  FOREIGN KEY (RecipientUserID) REFERENCES `user`(UserID)  ON DELETE CASCADE,
  INDEX idx_message_recipient (RecipientUserID, IsRead, CreatedAt)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS login_throttle (
  Kind         ENUM('User','Admin') NOT NULL,
  Identity     VARCHAR(100) NOT NULL,
  FailedCount  INT NOT NULL DEFAULT 0,
  LockedUntil  DATETIME NULL,
  UpdatedAt    DATETIME NOT NULL,
  PRIMARY KEY (Kind, Identity)
) ENGINE=InnoDB;

-- 7. The seeded default admin still uses admin123 -> ask for a password change at next login ---------
-- (The Java app verifies the hash; here we only flag the account named 'admin'. Harmless if it was already changed:
--  the warning disappears the moment the password is changed in the application.)
UPDATE admin SET MustChangePassword = 1 WHERE Username = 'admin' AND MustChangePassword = 0 AND CreatedBy IS NULL
  AND AdminID = (SELECT m FROM (SELECT MIN(AdminID) AS m FROM admin) t);

-- 8. PICKUP AREA (v2): all 64 districts + food.PickupArea ------------------------------------------
INSERT IGNORE INTO areas (AreaName) VALUES
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

-- old sub-district names of Bogura are folded into the district 'Bogura' (users keep working)
UPDATE `user` SET AreaID = 'Bogura' WHERE AreaID IN ('Bogura Sadar','Shajahanpur','Gabtali','Shibganj');

CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='food' AND COLUMN_NAME='PickupArea'",
 "ALTER TABLE food ADD COLUMN PickupArea VARCHAR(50) NULL AFTER ExpiryTime");
-- existing food: the pickup area becomes the donor's registered area
UPDATE food f JOIN `user` u ON u.UserID = f.UserID SET f.PickupArea = u.AreaID WHERE f.PickupArea IS NULL;
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='food' AND COLUMN_NAME='PickupArea' AND IS_NULLABLE='NO'",
 "ALTER TABLE food MODIFY PickupArea VARCHAR(50) NOT NULL");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='food' AND CONSTRAINT_NAME='fk_food_area'",
 "ALTER TABLE food ADD CONSTRAINT fk_food_area FOREIGN KEY (PickupArea) REFERENCES areas(AreaName) ON UPDATE CASCADE");
CALL sfw_exec_if(
 "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='food' AND INDEX_NAME='idx_food_pickup_area'",
 "ALTER TABLE food ADD INDEX idx_food_pickup_area (PickupArea, Status)");
-- the folded sub-district names are no longer in the list (only removed when nothing references them)
DELETE FROM areas WHERE AreaName IN ('Bogura Sadar','Shajahanpur','Gabtali','Shibganj')
  AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.AreaID = areas.AreaName)
  AND NOT EXISTS (SELECT 1 FROM food f WHERE f.PickupArea = areas.AreaName);

DROP PROCEDURE IF EXISTS sfw_exec_if;
