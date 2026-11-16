package inventory;



import alerts.NotificationManager;
import auth.UserSession;
import database.DBConnection;
import exceptions.UnauthorizedAccessException;
import models.Medicine;
import models.StaffMember;


import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



public class InventoryDAO {

    //Check and return database connection
    private Connection getValidConnection() throws SQLException {
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("Database connection is null!");
        }
        return conn;
    }

    //Check if logged-in user is an active owner
    private void verifyOwnerAccess() throws UnauthorizedAccessException {
        StaffMember currentUser = UserSession.getCurrentUser();
        if (currentUser == null || !"OWNER".equalsIgnoreCase(currentUser.getRole())
                || !"ACTIVE".equalsIgnoreCase(currentUser.getStatus())) {
            throw new UnauthorizedAccessException("Access Denied: Only an active Owner can perform this inventory action.");
        }
    }

    

    //Convert database row to Medicine object
    private Medicine mapRowToMedicine(ResultSet rs) throws SQLException {
        return new Medicine(
            rs.getInt("medicine_id"),
            rs.getString("medicine_name"),
            rs.getString("company_name"),
            rs.getString("batch_number"),
            rs.getDouble("unit_price"),
            rs.getInt("stock_quantity"),
            rs.getDate("stock_date"),
            rs.getDate("exp_date"),
            rs.getBoolean("requires_prescription"),
            rs.getString("moving_type")
        );
    }


    
    //Add new medicine batch (Owner only)
    public boolean addMedicineBatch(Medicine med) throws SQLException, UnauthorizedAccessException {
        verifyOwnerAccess();

        if (med == null) {
            throw new IllegalArgumentException("Medicine object cannot be null.");
        }
        if (med.getMedicineName() == null || med.getMedicineName().trim().isEmpty()) {
            throw new IllegalArgumentException("Medicine name cannot be empty.");
        }
        if (med.getBatchNumber() == null || med.getBatchNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Batch number cannot be empty.");
        }

        //Set default values for empty fields
        String companyName = (med.getCompanyName() != null && !med.getCompanyName().trim().isEmpty())
                             ? med.getCompanyName().trim() : "Unknown";
        String movingType = (med.getMovingType() != null && !med.getMovingType().trim().isEmpty())
                            ? med.getMovingType().trim().toUpperCase() : "SLOW";
        if (!movingType.equals("FAST") && !movingType.equals("SLOW")) {
            movingType = "SLOW";
        }

        String sql = "INSERT INTO inventory (medicine_name, company_name, batch_number, unit_price, " +
                     "stock_quantity, stock_date, exp_date, requires_prescription, moving_type) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, med.getMedicineName().trim());
            pstmt.setString(2, companyName);
            pstmt.setString(3, med.getBatchNumber().trim());
            pstmt.setDouble(4, med.getUnitPrice());
            pstmt.setInt(5, med.getStockQuantity());
            pstmt.setDate(6, med.getStockDate() != null ? med.getStockDate() : Date.valueOf(LocalDate.now()));
            pstmt.setDate(7, med.getExpDate());
            pstmt.setBoolean(8, med.isRequiresPrescription());
            pstmt.setString(9, movingType);

            boolean inserted = pstmt.executeUpdate() > 0;

            //Send expiry and stock alerts after adding
            if (inserted) {
                med.setMovingType(movingType);
                evaluateBatchExpiryAlert(med);
                evaluateTotalStockAlert(med.getMedicineName().trim(), movingType);
            }
            return inserted;
        }
    }

    

    //Search available medicine names for billing suggestions
    public List<String> searchMedicineNamesAutoSuggest(String keyword) throws SQLException {
        List<String> suggestions = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return suggestions;
        }

        String sql = "SELECT DISTINCT medicine_name FROM inventory " +
                     "WHERE medicine_name LIKE ? AND stock_quantity > 0 AND exp_date > CAST(GETDATE() AS DATE) " +
                     "ORDER BY medicine_name ASC";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, keyword.trim() + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("medicine_name"));
                }
            }
        }
        return suggestions;
    }

    

    //Search inventory by medicine name or batch number
    public List<Medicine> searchInventoryByNameOrBatch(String keyword) throws SQLException {
        List<Medicine> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllInventory();
        }

        String sql = "SELECT * FROM inventory " +
                     "WHERE medicine_name LIKE ? OR batch_number LIKE ? " +
                     "ORDER BY medicine_name ASC, exp_date ASC";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String pattern = "%" + keyword.trim() + "%";
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToMedicine(rs));
                }
            }
        }
        return results;
    }

    

    //Get valid batches sorted by earliest expiry date (FEFO)
    public List<Medicine> getBatchesByMedicineNameFEFO(String medicineName) throws SQLException {
        List<Medicine> batches = new ArrayList<>();
        if (medicineName == null || medicineName.trim().isEmpty()) {
            return batches;
        }

        String sql = "SELECT * FROM inventory " +
                     "WHERE medicine_name = ? AND stock_quantity > 0 AND exp_date > CAST(GETDATE() AS DATE) " +
                     "ORDER BY exp_date ASC";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicineName.trim());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapRowToMedicine(rs));
                }
            }
        }
        return batches;
    }

    

    //Get all inventory items
    public List<Medicine> getAllInventory() throws SQLException {
        List<Medicine> inventoryList = new ArrayList<>();
        String sql = "SELECT * FROM inventory ORDER BY medicine_name ASC, exp_date ASC";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                inventoryList.add(mapRowToMedicine(rs));
            }
        }
        return inventoryList;
    }

    

    //Get one medicine batch by ID
    public Medicine getMedicineById(int medicineId) throws SQLException {
        String sql = "SELECT * FROM inventory WHERE medicine_id = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, medicineId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToMedicine(rs);
                }
            }
        }
        return null;
    }

    

    //Change moving type to FAST or SLOW by name (Owner only)
    public boolean updateMovingType(String medicineName, String movingType) throws SQLException, UnauthorizedAccessException {
        verifyOwnerAccess();

        if (medicineName == null || medicineName.trim().isEmpty()) {
            throw new IllegalArgumentException("Medicine name cannot be empty.");
        }
        String formattedType = (movingType != null) ? movingType.trim().toUpperCase() : "SLOW";
        if (!formattedType.equals("FAST") && !formattedType.equals("SLOW")) {
            throw new IllegalArgumentException("Moving type must be strictly 'FAST' or 'SLOW'.");
        }

        String sql = "UPDATE inventory SET moving_type = ? WHERE medicine_name = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, formattedType);
            pstmt.setString(2, medicineName.trim());

            boolean updated = pstmt.executeUpdate() > 0;

            //Check low stock alert with new moving type
            if (updated) {
                evaluateTotalStockAlert(medicineName.trim(), formattedType);
            }
            return updated;
        }
    }

    

    //Change moving type to FAST or SLOW by ID (Owner only)
    public boolean updateMovingType(int medicineId, String movingType) throws SQLException, UnauthorizedAccessException {
        Medicine med = getMedicineById(medicineId);
        if (med != null) {
            return updateMovingType(med.getMedicineName(), movingType);
        }
        return false;
    }

    

    //Set moving type to FAST if over 300 units sold in last 7 days
    public void autoUpdateMovingTypes() throws SQLException {
        String sql = "UPDATE inventory " +
                     "SET moving_type = CASE " +
                     "    WHEN ( " +
                     "        SELECT ISNULL(SUM(bi.quantity), 0) " +
                     "        FROM bill_item bi " +
                     "        JOIN bills b ON bi.bill_id = b.bill_id " +
                     "        JOIN inventory i2 ON bi.medicine_id = i2.medicine_id " +
                     "        WHERE i2.medicine_name = inventory.medicine_name " +
                     "          AND b.create_at >= DATEADD(DAY, -7, GETDATE()) " +
                     "    ) > 300 THEN 'FAST' " +
                     "    ELSE 'SLOW' " +
                     "END";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.executeUpdate();
        }
    }

    

    //Reduce stock quantity when selling medicine
    public boolean deductStock(int medicineId, int quantitySold) throws SQLException {
        if (quantitySold <= 0) {
            throw new IllegalArgumentException("Quantity sold must be greater than zero.");
        }

        String sql = "UPDATE inventory SET stock_quantity = stock_quantity - ? " +
                     "WHERE medicine_id = ? AND stock_quantity >= ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantitySold);
            pstmt.setInt(2, medicineId);
            pstmt.setInt(3, quantitySold);

            boolean updated = pstmt.executeUpdate() > 0;

            //Check if total stock dropped below 50%
            if (updated) {
                Medicine updatedMed = getMedicineById(medicineId);
                if (updatedMed != null) {
                    evaluateTotalStockAlert(updatedMed.getMedicineName(), updatedMed.getMovingType());
                }
            }
            return updated;
        }
    }

    

    //Update stock quantity directly (Owner only)
    public boolean updateStockQuantity(int medicineId, int newQuantity) throws SQLException, UnauthorizedAccessException {
        verifyOwnerAccess();

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }

        String sql = "UPDATE inventory SET stock_quantity = ? WHERE medicine_id = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, medicineId);

            boolean updated = pstmt.executeUpdate() > 0;

            if (updated) {
                Medicine updatedMed = getMedicineById(medicineId);
                if (updatedMed != null) {
                    evaluateTotalStockAlert(updatedMed.getMedicineName(), updatedMed.getMovingType());
                }
            }
            return updated;
        }
    }


    
    //Set stock to 0 for expired batch (Owner only)
    public boolean clearExpiredBatchStock(int medicineId) throws SQLException, UnauthorizedAccessException {
        verifyOwnerAccess();

        String sql = "UPDATE inventory SET stock_quantity = 0 WHERE medicine_id = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, medicineId);
            return pstmt.executeUpdate() > 0;
        }
    }


    
    //Delete a batch from inventory (Owner only)
    public boolean deleteMedicineBatch(int medicineId) throws SQLException, UnauthorizedAccessException {
        verifyOwnerAccess();

        String sql = "DELETE FROM inventory WHERE medicine_id = ?";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, medicineId);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            //Handle error if batch is linked to past bills
            if (e.getErrorCode() == 547) {
                throw new SQLException("Cannot delete this batch because it is already linked to past sales bills. Set stock to 0 instead.");
            }
            throw e;
        }
    }

    

    //Run startup checks for moving types, expiry, and low stock
    public void checkAndGenerateStartupAlerts() throws SQLException {
        //Update FAST/SLOW tags using last 7 days sales
        autoUpdateMovingTypes();

        String sql = "SELECT * FROM inventory WHERE stock_quantity > 0";
        Set<String> checkedMedicinesForStock = new HashSet<>();

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Medicine med = mapRowToMedicine(rs);

                //Check expiry date for each batch
                evaluateBatchExpiryAlert(med);

                //Check total stock once per medicine name
                String medNameKey = med.getMedicineName().trim().toLowerCase();
                if (!checkedMedicinesForStock.contains(medNameKey)) {
                    checkedMedicinesForStock.add(medNameKey);
                    evaluateTotalStockAlert(med.getMedicineName(), med.getMovingType());
                }
            }
        }
    }

    

    //Check if batch is expired or expiring within 7 months
    public void evaluateBatchExpiryAlert(Medicine med) {
        if (med == null || med.getExpDate() == null) return;

        NotificationManager notifManager = NotificationManager.getInstance();
        LocalDate today = LocalDate.now();
        LocalDate sevenMonthsLater = today.plusMonths(7);
        LocalDate expLocal = med.getExpDate().toLocalDate();

        if (!expLocal.isAfter(sevenMonthsLater)) {
            if (expLocal.isBefore(today) || expLocal.isEqual(today)) {
                notifManager.addNotification(
                    "EXPIRED",
                    "EXPIRED Medicine: " + med.getMedicineName(),
                    "Batch [" + med.getBatchNumber() + "] expired on " + expLocal + ". Remove from shelf immediately!"
                );
            } else {
                notifManager.addNotification(
                    "EXPIRY_WARNING",
                    "Expiry Warning: " + med.getMedicineName(),
                    "Batch [" + med.getBatchNumber() + "] expiring soon (" + expLocal + "). Consider company re-export."
                );
            }
        }
    }

    

    //Check if total active stock is below 50% (FAST - 2500, SLOW  -100)
    public void evaluateTotalStockAlert(String medicineName, String movingType) throws SQLException {
        if (medicineName == null || medicineName.trim().isEmpty()) return;

        String sql = "SELECT ISNULL(SUM(stock_quantity), 0) AS total_active_stock " +
                     "FROM inventory " +
                     "WHERE medicine_name = ? AND stock_quantity > 0 AND exp_date > CAST(GETDATE() AS DATE)";

        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, medicineName.trim());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int totalActiveStock = rs.getInt("total_active_stock");
                    String type = (movingType != null) ? movingType.trim().toUpperCase() : "SLOW";

                    int standardStock = "FAST".equals(type) ? 5000 : 200;
                    int halfStockThreshold = standardStock / 2;

                    if (totalActiveStock < halfStockThreshold) {
                        int suggestedRestock = standardStock - totalActiveStock;
                        NotificationManager.getInstance().addNotification(
                            "LOW_STOCK",
                            "Low Stock (" + type + "): " + medicineName,
                            "Active stock (" + totalActiveStock + " units) dropped below 50% (" + halfStockThreshold + " units). Suggested restock: " + suggestedRestock + " units."
                        );
                    }
                }
            }
        }
    }
}
