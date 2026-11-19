-- ============================================================
-- Curo Pharmacy System - Database setup (SQL Server)
-- Safe to run more than once: nothing is dropped or duplicated.
-- Run the whole script in SSMS / VS Code MSSQL as an admin login.
-- ============================================================

-- Database Creation
IF DB_ID('curo_db') IS NULL
    CREATE DATABASE curo_db;
GO

USE curo_db;
GO

-- Application login (user name and password must match db.properties)
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'curoAdmin')
    CREATE LOGIN curoAdmin WITH PASSWORD = 'ict25954', CHECK_POLICY = OFF;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'curoAdmin')
    CREATE USER curoAdmin FOR LOGIN curoAdmin;
GO

ALTER ROLE db_owner ADD MEMBER curoAdmin;
GO

-- staff Table
IF OBJECT_ID('staff', 'U') IS NULL
CREATE TABLE staff(
	staff_id VARCHAR(50) PRIMARY KEY,
	staff_name VARCHAR(50) NOT NULL,
	role_ VARCHAR(50) NOT NULL CHECK (role_ IN ('OWNER', 'CASHIER')),
	contact_number VARCHAR(10) NOT NULL,
	password_ VARCHAR(50) NOT NULL,
	status_ VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status_ IN ('ACTIVE', 'INACTIVE'))
);
GO

-- inventory Table
IF OBJECT_ID('inventory', 'U') IS NULL
CREATE TABLE inventory(
	medicine_id INT IDENTITY(1,1) PRIMARY KEY,
	medicine_name VARCHAR(50) NOT NULL,
	company_name VARCHAR(50) NOT NULL,
	batch_number VARCHAR(50) NOT NULL,
	unit_price DECIMAL(10,2) NOT NULL,
	stock_quantity INT NOT NULL DEFAULT 0,
	stock_date DATE NOT NULL,
	exp_date DATE NOT NULL,
	requires_prescription BIT DEFAULT 0,
	moving_type VARCHAR(10) DEFAULT 'SLOW' CHECK (moving_type IN ('FAST', 'SLOW'))
);
GO

-- bills Table
IF OBJECT_ID('bills', 'U') IS NULL
CREATE TABLE bills(
	bill_id INT IDENTITY(1,1) PRIMARY KEY,
	cashier_id VARCHAR(50) FOREIGN KEY REFERENCES staff(staff_id),
	total_amount DECIMAL(10,2) NOT NULL,
	discount_amount DECIMAL(10,2) NOT NULL,
	net_total DECIMAL(10,2) NOT NULL,
	is_prescription_checked BIT DEFAULT 0,
	is_free_issue BIT DEFAULT 0,
	create_at DATETIME DEFAULT GETDATE()
);
GO

-- bill item Table
IF OBJECT_ID('bill_item', 'U') IS NULL
CREATE TABLE bill_item(
	item_id INT IDENTITY(1,1) PRIMARY KEY,
	bill_id INT FOREIGN KEY REFERENCES bills(bill_id),
	medicine_id INT FOREIGN KEY REFERENCES inventory(medicine_id),
	sold_price DECIMAL(10,2) NOT NULL,
	quantity INT NOT NULL,
	line_total DECIMAL(10,2) NOT NULL
);
GO

-- canceled bills Table
IF OBJECT_ID('canceled_bills', 'U') IS NULL
CREATE TABLE canceled_bills(
	cancel_id INT IDENTITY(1,10) PRIMARY KEY,
	bill_id INT NOT NULL,
	canceled_by VARCHAR(50) FOREIGN KEY REFERENCES staff(staff_id),
	canceled_at DATETIME DEFAULT GETDATE(),
	reason VARCHAR(50)
);
GO

-- Default Admin User
IF NOT EXISTS (SELECT 1 FROM staff WHERE staff_id = 'O001')
    INSERT INTO staff (staff_id,staff_name,role_,contact_number,password_,status_)
    VALUES ('O001','System Owner','OWNER','0758402616','ict25954','ACTIVE');
GO
