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
    INSERT INTO staff (staff_id, staff_name, role_, contact_number, password_, status_)
    VALUES ('O001', 'System Owner', 'OWNER', '0758402616', '1234', 'ACTIVE');
GO



IF NOT EXISTS (SELECT 1 FROM staff WHERE staff_id = 'C001')
    INSERT INTO staff (staff_id, staff_name, role_, contact_number, password_, status_)
    VALUES ('C001', 'Sachintha', 'CASHIER', '0763412872', '0987', 'ACTIVE');
GO

-- Default Medicine
If NOT EXISTS (SELECT 1 FROM inventory WHERE medicine_id = 1)
INSERT INTO inventory (
    medicine_name, 
    company_name, 
    batch_number, 
    unit_price, 
    stock_quantity, 
    stock_date, 
    exp_date, 
    requires_prescription, 
    moving_type
)
VALUES 
    ('Paracetamol', 'State Pharmaceuticals Corp', 'BATCH001', 3.50, 500, '2026-01-10', '2028-01-10', 0, 'FAST'),
    ('Amoxicillin', 'Pfizer', 'BATCH002', 18.50, 200, '2026-02-15', '2027-08-15', 1, 'FAST'),
    ('Cetirizine', 'Cipla', 'BATCH003', 4.50, 1000, '2026-03-01', '2029-03-01', 0, 'SLOW'),
    ('Ibuprofen', 'Novartis', 'BATCH004', 8.00, 450, '2026-01-20', '2028-06-20', 0, 'FAST'),
    ('Metformin', 'Sun Pharma', 'BATCH005', 10.00, 600, '2026-04-10', '2028-10-10', 1, 'SLOW'),
    ('Omeprazole', 'Dr. Reddy', 'BATCH006', 22.00, 300, '2026-02-01', '2027-12-01', 1, 'FAST'),
    ('Aspirin', 'Bayer', 'BATCH007', 5.00, 800, '2026-01-05', '2029-01-05', 0, 'SLOW'),
    ('Azithromycin', 'Cipla', 'BATCH008', 55.00, 150, '2026-03-12', '2027-09-12', 1, 'FAST'),
    ('Vitamin C', 'GSK', 'BATCH009', 12.00, 400, '2026-05-01', '2028-05-01', 0, 'SLOW'),
    ('Losartan', 'Torrent', 'BATCH010', 16.50, 350, '2026-04-15', '2028-11-15', 1, 'SLOW');
GO


SELECT staff_id, staff_name, role_, status_
FROM dbo.staff;


-- Default Bill
-- Make sure the cashier_id values below exist in your staff table
If NOT EXISTS (SELECT 1 FROM bills WHERE bill_id = 1)
INSERT INTO bills (
    cashier_id,
    total_amount,
    discount_amount,
    net_total,
    is_prescription_checked,
    is_free_issue,
    create_at
)
VALUES
    -- Thu 2026-11-16: Cetirizine (4.50 x 20) + Vitamin C (12.00 x 10) = 210.00, disc 10.00 -> 200.00
    ('C001', 210.00, 10.00, 200.00, 0, 0, '2026-11-16 09:15:00'),

    -- Thu 2026-11-16: Ibuprofen (8.00 x 50) + Omeprazole (22.00 x 20) = 840.00, disc 40.00 -> 800.00
    ('C001', 840.00, 40.00, 800.00, 1, 0, '2026-11-16 10:05:00'),

    -- Thu 2026-11-17: Paracetamol (3.50 x 100) + Metformin (10.00 x 50) = 850.00, disc 0.00 -> 850.00
    ('C001', 850.00, 0.00, 850.00, 1, 0, '2026-11-17 10:40:00'),

    -- Thu 2026-11-17: Aspirin (5.00 x 10) = 50.00 free issue -> net 0.00
    ('C001', 50.00, 50.00, 0.00, 0, 1, '2026-11-17 10:55:00'),

    -- Thu 2026-11-18: Losartan (16.50 x 10) = 165.00, disc 115.00 -> net 50.00
    ('O001', 165.00, 115.00, 50.00, 1, 0, '2026-11-18 11:05:00'),

    -- Thu 2026-11-18: Cetirizine (4.50 x 20) + Vitamin C (12.00 x 10) = 210.00, disc 10.00 -> 200.00
    ('C001', 210.00, 10.00, 200.00, 0, 0, '2026-11-18 09:15:50'),

    -- Thu 2026-11-18: Ibuprofen (8.00 x 50) + Omeprazole (22.00 x 20) = 840.00, disc 40.00 -> 800.00
    ('C001', 840.00, 40.00, 800.00, 1, 0, '2026-11-18 10:25:00'),

    -- Thu 2026-11-19: Paracetamol (3.50 x 100) + Metformin (10.00 x 50) = 850.00, disc 0.00 -> 850.00
    ('O001', 850.00, 0.00, 850.00, 1, 0, '2026-11-19 10:43:00'),

    -- Thu 2026-11-19: Aspirin (5.00 x 10) = 50.00 free issue -> net 0.00
    ('C001', 50.00, 50.00, 0.00, 0, 1, '2026-11-19 10:35:00'),

    -- Thu 2026-11-19: Losartan (16.50 x 10) = 165.00, disc 115.00 -> net 50.00
    ('C001', 165.00, 115.00, 50.00, 1, 0, '2026-11-19 11:45:00');
GO
