package inventory;

import alerts.NotificationManagaer;
import auth.UserDAO;
import database.DBConnection;
import exceptions.ExpiredMedicineException;
import exceptions.InvalidStaffIdException;
import exceptions.OutOfStockException;
import exceptions.UnauthorizedAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import models.Medicine;

public class InventoryDAO {

    // Instantiate UserDAO to handle security and role verification
    private final UserDAO userDao = new UserDAO();

    // Helper method to map a ResultSet row to a Medicine object.
    private Medicine mapResultSetToMedicine(ResultSet rs) throws SQLException {
        Medicine med = new Medicine();
        med.setMedicineId(rs.getInt("medicine_id"));
        med.setMedicineName(rs.getString("medicine_name"));
        med.setCompanyName(rs.getString("company_name"));
        med.setBatchNumber(rs.getString("batch_number"));
        med.setUnitPrice(rs.getDouble("unit_price"));
        med.setStockQuantity(rs.getInt("stock_quantity"));
        med.setStockDate(rs.getDate("stock_date"));
        med.setExpDate(rs.getDate("exp_date"));
        med.setRequiresPrescription(rs.getBoolean("requires_prescription"));
        med.setMovingType(rs.getString("moving_type"));
        return med;
    }

    // 1. Inserts a new medicine batch into inventory table (owner only).
    public boolean addMedicineBatch(Medicine med, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        userDao.verifyActiveOwner(staffId);

        if (med == null) {
            throw new IllegalArgumentException("Medicine object cannot be null.");
        }
        if (med.getMedicineName() == null || med.getMedicineName().trim().isEmpty()) {
            throw new IllegalArgumentException("Medicine name cannot be empty.");
        }
        if (med.getBatchNumber() == null || med.getBatchNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Batch number cannot be empty.");
        }

        String companyName = (med.getCompanyName() == null || med.getCompanyName().trim().isEmpty()) ? "Unknown" : med.getCompanyName().trim();
        String movingType = med.getMovingType();
        if (movingType == null || (!movingType.equalsIgnoreCase("FAST") && !movingType.equalsIgnoreCase("SLOW"))) {
            movingType = "SLOW";
        } else {
            movingType = movingType.toUpperCase();
        }
        java.sql.Date stockDate = (med.getStockDate() != null) ? med.getStockDate() : java.sql.Date.valueOf(LocalDate.now());
        
        String query = "INSERT INTO inventory (medicine_name, company_name, batch_number, unit_price, stock_quantity, stock_date, exp_date, requires_prescription, moving_type) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, med.getMedicineName().trim());
            stmt.setString(2, companyName);
            stmt.setString(3, med.getBatchNumber().trim());
            stmt.setDouble(4, med.getUnitPrice());
            stmt.setInt(5, med.getStockQuantity());
            stmt.setDate(6, stockDate);
            stmt.setDate(7, med.getExpDate());
            stmt.setBoolean(8, med.isRequiresPrescription());
            stmt.setString(9, movingType);

            int rowsInserted = stmt.executeUpdate();
            
            if (rowsInserted > 0) {
                evaluateBatchExpiry(med);
                evaluateTotalStock(med.getMedicineName().trim());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error Adding Medicine Record: " + e.getMessage());
            throw e;
        }
        return false;
    }

    // 2. Live search by medicine name or batch number for auto-suggest support.
    public List<Medicine> searchMedicinesByName(String keyword) {
        List<Medicine> medicineList = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return medicineList;
        }

        String query = "SELECT MIN(medicine_id) AS medicine_id, medicine_name, MIN(company_name) AS company_name, " +
                       "MIN(batch_number) AS batch_number, MIN(unit_price) AS unit_price, SUM(stock_quantity) AS stock_quantity, " +
                       "MIN(stock_date) AS stock_date, MIN(exp_date) AS exp_date, MIN(requires_prescription) AS requires_prescription, " +
                       "MIN(moving_type) AS moving_type FROM inventory " +
                       "WHERE (medicine_name LIKE ? OR batch_number LIKE ?) AND stock_quantity > 0 AND exp_date > CAST(GETDATE() AS DATE) " +
                       "GROUP BY medicine_name ORDER BY medicine_name ASC";
                       
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            String searchPattern = keyword.trim() + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    medicineList.add(mapResultSetToMedicine(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error Searching Medicine Records: " + e.getMessage());
        }

        return medicineList;
    }

    // 3. Reduces stock using FEFO and Stops Billing Expired Medicines.
    public boolean reduceStockByFEFO(int medicineId, int requestedQuantity) throws SQLException, ExpiredMedicineException {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        }

        String selectQuery = "SELECT medicine_name, batch_number, stock_quantity, exp_date FROM inventory "
                            + "WHERE medicine_id = ? AND stock_quantity > 0 "
                            + "ORDER BY exp_date ASC";
                       
        String updateQuery = "UPDATE inventory SET stock_quantity = ? WHERE batch_number = ?";
        LocalDate today = LocalDate.now();
        String targetMedicineName = null;

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery);
                PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {

                selectStmt.setInt(1, medicineId);
            
                try (ResultSet rs = selectStmt.executeQuery()) {
                    int remainingToDeduct = requestedQuantity;

                    while (rs.next() && remainingToDeduct > 0) {
                        targetMedicineName = rs.getString("medicine_name");
                        String batchNumber = rs.getString("batch_number");
                        int currentStock = rs.getInt("stock_quantity");
                        java.sql.Date expDateSql = rs.getDate("exp_date");

                        if (expDateSql != null){
                            LocalDate expiration = expDateSql.toLocalDate();
                            if (expiration.isBefore(today) || expiration.isEqual(today)) {
                                conn.rollback();
                                throw new ExpiredMedicineException("Billing Blocked: Cannot dispense expired batch " + batchNumber + " Expired on: " + expiration + ").");
                            }
                        }

                        int deductFromThisBatch = Math.min(currentStock, remainingToDeduct);
                        int newStock = currentStock - deductFromThisBatch;

                        updateStmt.setInt(1, newStock);
                        updateStmt.setString(2, batchNumber);
                        updateStmt.executeUpdate();

                        remainingToDeduct -= deductFromThisBatch;
                    }

                    if (remainingToDeduct > 0) {
                        conn.rollback();
                        throw new SQLException("Out of Stock: Insufficient inventory across batches to fulfill quantity.");
                    }

                    conn.commit();

                    if (targetMedicineName != null) {
                        evaluateTotalStock(targetMedicineName);
                    }
                    return true;
                }
            } catch (SQLException | ExpiredMedicineException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // 5. Get Single Batch By Id.
    public Medicine getMedicineById(int medicineId) {
        Medicine med = null;
        String query = "SELECT * FROM inventory WHERE medicine_id = ?";

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, medicineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    med = mapResultSetToMedicine(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error Fetching Medicine By ID: " + e.getMessage());
        }
        return med;
    }

    // Retrieves all available batches for a specific medicine name, sorted by FEFO
    public List<Medicine> getBatchesForMedicineFEFO(String medicineName) {
        List<Medicine> list = new ArrayList<>();
        if (medicineName == null || medicineName.trim().isEmpty()) {
            return list;
        }

        String query = "SELECT * FROM inventory WHERE medicine_name = ? AND stock_quantity > 0 AND exp_date > GETDATE() ORDER BY exp_date ASC";

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, medicineName.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToMedicine(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error Fetching FEFO Batches for Medicine: " + e.getMessage());
        }

        return list;
    }

    // 4. Display all inventory records from MSSQL.
    public List<Medicine> getAllInventory() {
        List<Medicine> list = new ArrayList<>();
        String query = "SELECT * FROM inventory ORDER BY medicine_name ASC, exp_date ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToMedicine(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error Fetching Inventory Items: " + e.getMessage());
        }

        return list;
    }

    // Updates stock quantity for a specific medicine record by medicine_id (owner only).
    public boolean updateQuantity(int medicineId, int newQty, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        userDao.verifyActiveOwner(staffId);

        String query = "UPDATE inventory SET stock_quantity = ? WHERE medicine_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, newQty);
            stmt.setInt(2, medicineId);

            return stmt.executeUpdate() > 0 ;
        } catch (SQLException e) {
            System.err.println("Error Updating Quantity: " + e.getMessage());
            return false;
        }
    }

    // 6A. Updates a medicine's moving classification manually (FAST or SLOW) (owner only)
    public boolean updateMovingType(String medicineName, String movingType, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        userDao.verifyActiveOwner(staffId);

        if (movingType == null || (!movingType.equalsIgnoreCase("FAST") && !movingType.equalsIgnoreCase("SLOW"))) {
            throw new IllegalArgumentException("Moving type must be strictly 'FAST' or 'SLOW'.");
        }

        String normalizedType = movingType.toUpperCase();
        String query = "UPDATE inventory SET moving_type = ? WHERE medicine_name = ?";

        try (Connection conn = DBConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, normalizedType);
            stmt.setString(2, medicineName.trim());

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error Updating Moving Type: " + e.getMessage());
            throw e;
        }
    }

    public boolean updateMovingType(int medicineId, String movingType, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        Medicine med = getMedicineById(medicineId);
        if (med != null) {
            return updateMovingType(med.getMedicineName(), movingType, staffId);
        }
        return false;
    }

    // 6B. AUTO MOVING TYPE CLASSIFICATION (BASED ON 7-DAY SALES)
    public void updateMovingTypeAutomatically() {
        String query = "UPDATE inv " +
                       "SET inv.moving_type = CASE WHEN ISNULL(sales.total_qty, 0) >= 100 THEN 'FAST' ELSE 'SLOW' END " +
                       "FROM inventory inv " +
                       "LEFT JOIN (" +
                       "    SELECT bi.medicine_name, SUM(bi.quantity) AS total_qty " +
                       "    FROM bill_item bi " +
                       "    JOIN bills b ON bi.bill_id = b.bill_id " +
                       "    WHERE b.create_at >= DATEADD(day, -7, GETDATE()) " +
                       "    GROUP BY bi.medicine_name" +
                       ") sales ON inv.medicine_name = sales.medicine_name";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error Auto-Updating Moving Type: " + e.getMessage());
        }
    }

    // Deletes an inventory record by medicine_id (owner only).
    public boolean deleteMedicine(int medicineId, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        userDao.verifyActiveOwner(staffId);

        String query = "DELETE FROM inventory WHERE medicine_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, medicineId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error Deleting Medicine: " + e.getMessage());
            return false;
        }
    }

    // 8. Sets stock_quantity to 0 when physically removing expired items from shelves.
    public boolean clearExpiredBatchStock(int medicineId, String staffId) throws SQLException, UnauthorizedAccessException, InvalidStaffIdException {
        userDao.verifyActiveOwner(staffId);

        String query = "UPDATE inventory SET stock_quantity = 0 WHERE medicine_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, medicineId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error Clearing Expired Batch Stock: " + e.getMessage());
            throw e;
        }
    }

    // 9. SYSTEM STARTUP ALERT SCAN
    public void runSystemStartupAlertScan() {
        // FIRST: Run Auto Moving Type Classification
        updateMovingTypeAutomatically();

        List<Medicine> activeBatches = new ArrayList<>();
        Set<String> evaluatedMedicines = new HashSet<>();

        String query = "SELECT * FROM inventory WHERE stock_quantity > 0";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Medicine med = mapResultSetToMedicine(rs);
                activeBatches.add(med);
                evaluateBatchExpiry(med);
            }

            for (Medicine med : activeBatches) {
                if (evaluatedMedicines.add(med.getMedicineName())) {
                    evaluateTotalStock(med.getMedicineName());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error During System Startup Alert Scan: " + e.getMessage());
        }
    }

    // ** ALERT EVALUATION HELPERS (PUSH TO NotificationManager) ** //
    
    // A. Batch Expiry Alert Logic
    public void evaluateBatchExpiry(Medicine med) {
        if (med == null || med.getExpDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiration = med.getExpDate().toLocalDate();
        LocalDate thresholdDate = today.plusMonths(7);

        if (expiration.isBefore(today) || expiration.isEqual(today)) {
            NotificationManagaer.getInstance().addNotification(
                "EXPIRED", 
                med.getMedicineName(), 
                "Batch " + med.getBatchNumber() + " has expired! Immediate removal from shelves required."
            );
        } else if (!expiration.isAfter(thresholdDate)) {
            NotificationManagaer.getInstance().addNotification(
                "EXPIRY_WARNING", 
                med.getMedicineName(), 
                "Batch " + med.getBatchNumber() + " expires on " + expiration + ". Suggested company re-export."
            );
        }
    }

    // B. Total Active Stock Alert Logic (50% Restock Rule Fixed)
    public void evaluateTotalStock(String medicineName) {
        if (medicineName == null || medicineName.trim().isEmpty()) {
            return;
        }

        String query = "SELECT SUM(stock_quantity) AS total_stock, MAX(moving_type) AS moving_type "
                   + "FROM inventory WHERE medicine_name = ? AND stock_quantity > 0 AND exp_date > GETDATE()";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, medicineName.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int totalStock = rs.getInt("total_stock");
                    String movingType = rs.getString("moving_type");
                    if (movingType == null) {
                        movingType = "SLOW";
                    }

                    int lowThreshold;
                    int restockAmount;

                    if ("FAST".equalsIgnoreCase(movingType)) {
                        lowThreshold = 500; // Alert when active stock < 500 units
                        restockAmount = 1000 - totalStock;
                    } else {
                        lowThreshold = 50;  // Alert when active stock < 50 units
                        restockAmount = 100 - totalStock;
                    }

                    if (totalStock < lowThreshold) {
                        NotificationManagaer.getInstance().addNotification(
                            "LOW_STOCK", 
                            medicineName, 
                            "Total active stock is " + totalStock + " units (Threshold: " + lowThreshold + "). Suggested restock: " + restockAmount + " units."
                        );
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error Evaluating Total Stock for " + medicineName + ": " + e.getMessage());
        }
    }

    // Method to check if a cashier is trying to sell more items than are physically available
    public void validateStockAvailability(int medicineId, int requestedQty) throws OutOfStockException {
        Medicine med = getMedicineById(medicineId);
        if (med == null) {
            throw new IllegalArgumentException("Medicine record cannot be null.");
        }
    
        if (requestedQty > med.getStockQuantity()) {
            throw new OutOfStockException(
                "Billing Blocked: Insufficient stock for " + med.getMedicineName() + " (Available: " + med.getStockQuantity() + ", Requested: " + requestedQty + ")."
            );
        }
    }
}