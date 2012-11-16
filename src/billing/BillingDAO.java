package billing;

import alerts.NotificationManager;
import auth.UserSession;
import database.DBConnection;
import exceptions.ExpiredMedicineException;
import exceptions.OutOfStockException;
import exceptions.UnauthorizedAccessException;
import inventory.InventoryDAO;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import models.Bill;
import models.BillItem;
import models.CanceledBill;
import models.Medicine;
import models.StaffMember;

public class BillingDAO {

    /**
     * =========================================================================
     * 1. PROCESS BILL TRANSACTION (FEFO, SECURITY & TRANSACTION SAFETY)
     * =========================================================================
     * Purpose:
     * - Validates the bill object and its items list to ensure they are not empty.
     * - SECURITY CHECK 1: Verifies that Free Issue bills are authorized strictly by an active OWNER.
     * - Merges duplicate items for the same medicine and validates quantities > 0.
     * - Starts a database transaction (setAutoCommit(false)) for all-or-nothing data safety.
     * - SECURITY CHECK 2: Verifies prescription requirements against user role or prescription checkbox.
     * - Performs FEFO (First Expired, First Out) batch-wise stock deduction from inventory table.
     * - Calculates total amounts, 5% discounts (attaching the selected discount reason), and net totals.
     * - Inserts the main bill record and batch-inserts line items into the database.
     * - Commits the transaction if successful, or rolls back entirely if any error occurs.
     * - Triggers post-billing moving-type updates and low-stock alerts outside the transaction.
     */
    public int processBillTransaction(Bill bill) throws SQLException, UnauthorizedAccessException, OutOfStockException, ExpiredMedicineException {
        // Validation: Check if bill and items list are present
        if (bill == null || bill.getItems() == null || bill.getItems().isEmpty()) {
            throw new IllegalArgumentException("Bill object and its items list cannot be null or empty.");
        }

        // SECURITY CHECK 1 (Free Issue): Ensure session user is an OWNER if Free Issue is active
        if (bill.isFreeIssue()) {
            StaffMember currentUser = UserSession.getCurrentUser();
            if (currentUser == null || !"OWNER".equalsIgnoreCase(currentUser.getRole())) {
                throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can authorize Free Issue bills.");
            }
        }

        // MERGE DUPLICATE ITEMS: Combine quantities if the same medicine appears multiple times in the bill
        Map<Integer, Integer> mergedItemsMap = new HashMap<>();
        Map<Integer, String> medicineNamesMap = new HashMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Every item quantity must be greater than 0.");
            }
            mergedItemsMap.put(item.getMedicineId(), mergedItemsMap.getOrDefault(item.getMedicineId(), 0) + item.getQuantity());
            medicineNamesMap.put(item.getMedicineId(), item.getMedicineName());
        }

        Connection conn = null;
        int generatedBillId = -1;

        try {
            conn = DBConnection.getConnection();
            if (conn == null) {
                throw new SQLException("Database connection failed: Connection is null.");
            }

            // START DATABASE TRANSACTION
            conn.setAutoCommit(false);

            List<BillItem> finalizedBillItems = new ArrayList<>();
            double totalAmount = 0.0;

            // Loop through each unique requested medicine
            for (Map.Entry<Integer, Integer> entry : mergedItemsMap.entrySet()) {
                int medicineId = entry.getKey();
                int requestedQty = entry.getValue();
                String medName = medicineNamesMap.get(medicineId);

                // Fetch prescription requirement and medicine name from inventory
                boolean requiresRx = false;
                String checkRxSql = "SELECT requires_prescription, medicine_name FROM inventory WHERE medicine_id = ?";
                try (PreparedStatement rxStmt = conn.prepareStatement(checkRxSql)) {
                    rxStmt.setInt(1, medicineId);
                    try (ResultSet rxRs = rxStmt.executeQuery()) {
                        if (rxRs.next()) {
                            requiresRx = rxRs.getBoolean("requires_prescription");
                            medName = rxRs.getString("medicine_name");
                        }
                    }
                }

                // SECURITY CHECK 2 (Prescription): Verify if prescription is checked or user is Owner
                if (requiresRx && !bill.isPrescriptionChecked()) {
                    StaffMember currentUser = UserSession.getCurrentUser();
                    boolean isOwner = currentUser != null && "OWNER".equalsIgnoreCase(currentUser.getRole());
                    if (!isOwner) {
                        conn.rollback();
                        throw new UnauthorizedAccessException("Prescription verification required for medicine: " + medName);
                    }
                }

                // a) Fetch active, non-expired batches sorted by earliest expiry date first (FEFO)
                String batchSql = "SELECT medicine_id, unit_price, stock_quantity, exp_date FROM inventory " +
                                  "WHERE medicine_id = ? AND stock_quantity > 0 AND exp_date > GETDATE() " +
                                  "ORDER BY exp_date ASC";

                List<BatchStock> activeBatches = new ArrayList<>();
                int totalAvailableStock = 0;

                try (PreparedStatement batchStmt = conn.prepareStatement(batchSql)) {
                    batchStmt.setInt(1, medicineId);
                    try (ResultSet batchRs = batchStmt.executeQuery()) {
                        while (batchRs.next()) {
                            int stock = batchRs.getInt("stock_quantity");
                            totalAvailableStock += stock;
                            activeBatches.add(new BatchStock(
                                batchRs.getInt("medicine_id"),
                                batchRs.getDouble("unit_price"),
                                stock,
                                batchRs.getDate("exp_date")
                            ));
                        }
                    }
                }

                // b) If no active batches exist, check if only expired stock remains
                if (activeBatches.isEmpty()) {
                    String expiredCheckSql = "SELECT COUNT(*) FROM inventory WHERE medicine_id = ? AND stock_quantity > 0 AND exp_date <= GETDATE()";
                    try (PreparedStatement expStmt = conn.prepareStatement(expiredCheckSql)) {
                        expStmt.setInt(1, medicineId);
                        try (ResultSet expRs = expStmt.executeQuery()) {
                            if (expRs.next() && expRs.getInt(1) > 0) {
                                conn.rollback();
                                throw new ExpiredMedicineException("ExpiredMedicineException: Only expired stock remains for medicine ID " + medicineId);
                            }
                        }
                    }
                    conn.rollback();
                    throw new OutOfStockException("OutOfStockException: No stock available for medicine ID " + medicineId);
                }

                // d) Check if combined stock across active batches is enough to fulfill the requested quantity
                if (totalAvailableStock < requestedQty) {
                    conn.rollback();
                    throw new OutOfStockException("OutOfStockException: Insufficient stock for " + medName + ". Requested: " + requestedQty + ", Available: " + totalAvailableStock);
                }

                // e) FEFO DEDUCTION LOOP & f) Deduct stock batch-by-batch and record finalized line items
                int remainingQtyToDeduct = requestedQty;
                for (BatchStock batch : activeBatches) {
                    if (remainingQtyToDeduct <= 0) break;

                    int qtyTakenFromBatch = Math.min(batch.stockQuantity, remainingQtyToDeduct);
                    double unitPrice = batch.unitPrice;
                    double lineTotal = unitPrice * qtyTakenFromBatch;

                    totalAmount += lineTotal;

                    // Update inventory stock inside the active transaction
                    String updateStockSql = "UPDATE inventory SET stock_quantity = stock_quantity - ? WHERE medicine_id = ? AND unit_price = ? AND exp_date = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateStockSql)) {
                        updateStmt.setInt(1, qtyTakenFromBatch);
                        updateStmt.setInt(2, batch.medicineId);
                        updateStmt.setDouble(3, batch.unitPrice);
                        updateStmt.setDate(4, batch.expDate);
                        updateStmt.executeUpdate();
                    }

                    finalizedBillItems.add(new BillItem(batch.medicineId, medName, unitPrice, qtyTakenFromBatch));
                    remainingQtyToDeduct -= qtyTakenFromBatch;
                }
            }

            // CALCULATE TOTALS & DISCOUNTS (Including Discount Reason Handling)
            double discountAmount = 0.0;
            double netTotal = totalAmount;

            if (bill.isFreeIssue()) {
                discountAmount = totalAmount;
                netTotal = 0.0;
                bill.setDiscountReason("Free Issue (Owner Authorized)");
            } else {
                String reason = bill.getDiscountReason();
                if (reason != null && !reason.trim().isEmpty() && !"None".equalsIgnoreCase(reason.trim())) {
                    discountAmount = Math.round((totalAmount * 0.05) * 100.0) / 100.0; // 5% discount rounded to 2 decimals
                    netTotal = Math.max(0.0, totalAmount - discountAmount);
                    bill.setDiscountReason(reason.trim());
                } else {
                    bill.setDiscountReason("None");
                }
            }

            bill.setTotalAmount(totalAmount);
            bill.setDiscountAmount(discountAmount);
            bill.setNetTotal(netTotal);

            // SAVE TO DATABASE: Insert into 'bills' table and retrieve generated bill_id
            String insertBillSql = "INSERT INTO bills (cashier_id, total_amount, discount_amount, net_total, is_prescription_checked, is_free_issue, create_at) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, GETDATE())";

            try (PreparedStatement billStmt = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                billStmt.setString(1, bill.getCashierId());
                billStmt.setDouble(2, totalAmount);
                billStmt.setDouble(3, discountAmount);
                billStmt.setDouble(4, netTotal);
                billStmt.setBoolean(5, bill.isPrescriptionChecked());
                billStmt.setBoolean(6, bill.isFreeIssue());
                billStmt.executeUpdate();

                try (ResultSet rs = billStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedBillId = rs.getInt(1);
                        bill.setBillId(generatedBillId);
                    }
                }
            }

            // Batch-insert all finalized batch line items into 'bill_item' table
            String insertItemSql = "INSERT INTO bill_item (bill_id, medicine_id, sold_price, quantity, line_total) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql)) {
                for (BillItem item : finalizedBillItems) {
                    itemStmt.setInt(1, generatedBillId);
                    itemStmt.setInt(2, item.getMedicineId());
                    itemStmt.setDouble(3, item.getSoldPrice());
                    itemStmt.setInt(4, item.getQuantity());
                    itemStmt.setDouble(5, item.getLineTotal());
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();
            }

            // COMMIT TRANSACTION
            conn.commit();
            bill.setItems(finalizedBillItems);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Rollback transaction if any error occurs
                } catch (SQLException ex) {
                    System.err.println("Rollback failed: " + ex.getMessage());
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close(); // Close database connection
                } catch (SQLException e) {
                    System.err.println("Failed to close connection: " + e.getMessage());
                }
            }
        }

        // POST-BILLING VELOCITY & STOCK ALERTS (OUTSIDE TRANSACTION)
        try {
            InventoryDAO inventoryDAO = new InventoryDAO();
            inventoryDAO.autoUpdateMovingTypes(); // Automatically re-classify moving types based on last 7 days of sales
            for (BillItem item : bill.getItems()) {
                Medicine med = inventoryDAO.getMedicineById(item.getMedicineId());
                if (med != null) {
                    inventoryDAO.evaluateTotalStockAlert(med.getMedicineName(), med.getMovingType()); // Check 50% restock threshold
                }
            }
        } catch (Exception alertEx) {
            System.err.println("Post-billing alert warning: " + alertEx.getMessage());
        }

        return generatedBillId;
    }

    /**
     * =========================================================================
     * 2. CANCEL AN EXISTING BILL (OWNER ONLY + STOCK REVERSAL)
     * =========================================================================
     * Purpose:
     * - Restricts bill cancellation exclusively to active Owners (Security Check).
     * - Validates that the bill has not been canceled previously.
     * - Fetches billed items and restores quantities back to their inventory batches.
     * - Inserts a cancellation record into the 'canceled_bills' table.
     * - Triggers a post-cancellation notification alert.
     */
    public boolean cancelPastBill(int billId, String reason) throws SQLException, UnauthorizedAccessException, IllegalStateException {
        // SECURITY CHECK: Only an OWNER is allowed to cancel bills
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can cancel bills.");
        }

        if (isBillCanceled(billId)) {
            throw new IllegalStateException("Bill ID " + billId + " is already canceled.");
        }

        // Validate and trim cancellation reason
        String trimmedReason = (reason == null || reason.trim().isEmpty()) ? "Canceled by Owner" : reason.trim();
        if (trimmedReason.length() > 50) {
            trimmedReason = trimmedReason.substring(0, 50);
        }

        StaffMember currentOwner = UserSession.getCurrentUser();
        String ownerId = (currentOwner != null) ? currentOwner.getStaffId() : "ADMIN01";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            if (conn == null) throw new SQLException("Database connection failed.");

            conn.setAutoCommit(false);

            // Fetch all medicine IDs and quantities for this bill from 'bill_item'
            String fetchItemsSql = "SELECT medicine_id, quantity FROM bill_item WHERE bill_id = ?";
            List<BillItem> billItems = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(fetchItemsSql)) {
                ps.setInt(1, billId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BillItem item = new BillItem();
                        item.setMedicineId(rs.getInt("medicine_id"));
                        item.setQuantity(rs.getInt("quantity"));
                        billItems.add(item);
                    }
                }
            }

            if (billItems.isEmpty()) {
                conn.rollback();
                throw new IllegalArgumentException("No items found for Bill ID " + billId);
            }

            // Restore stock quantities back to inventory table
            String restoreStockSql = "UPDATE inventory SET stock_quantity = stock_quantity + ? WHERE medicine_id = ?";
            try (PreparedStatement restoreStmt = conn.prepareStatement(restoreStockSql)) {
                for (BillItem item : billItems) {
                    restoreStmt.setInt(1, item.getQuantity());
                    restoreStmt.setInt(2, item.getMedicineId());
                    restoreStmt.executeUpdate();
                }
            }

            // Insert record into 'canceled_bills' table
            String insertCancelSql = "INSERT INTO canceled_bills (bill_id, canceled_by, canceled_at, reason) VALUES (?, ?, GETDATE(), ?)";
            try (PreparedStatement cancelStmt = conn.prepareStatement(insertCancelSql)) {
                cancelStmt.setInt(1, billId);
                cancelStmt.setString(2, ownerId);
                cancelStmt.setString(3, trimmedReason);
                cancelStmt.executeUpdate();
            }

            conn.commit();

            // POST-CANCEL NOTIFICATION
            try {
                NotificationManager.getInstance().addNotification("BILL_CANCELED","Bill #" + billId + " Canceled","Owner: " + ownerId + ", Reason: " + trimmedReason);
            } catch (Exception notifEx) {
                System.err.println("Notification warning: " + notifEx.getMessage());
            }

            return true;
        } catch (Exception e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * =========================================================================
     * 3. GET ALL CANCELED BILLS (OWNER ONLY)
     * =========================================================================
     * Purpose:
     * - Verifies that the current user has active Owner privileges.
     * - Retrieves all canceled bill history records ordered by cancellation date descending.
     */
    public List<CanceledBill> getAllCanceledBills() throws SQLException, UnauthorizedAccessException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can view canceled bills.");
        }

        List<CanceledBill> canceledList = new ArrayList<>();
        String sql = "SELECT cancel_id, bill_id, canceled_by, canceled_at, reason FROM canceled_bills ORDER BY canceled_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                canceledList.add(new CanceledBill(
                    rs.getInt("cancel_id"),
                    rs.getInt("bill_id"),
                    rs.getString("canceled_by"),
                    rs.getTimestamp("canceled_at"),
                    rs.getString("reason")
                ));
            }
        }
        return canceledList;
    }

    /**
     * =========================================================================
     * 4. GET BILL DETAILS BY ID (FOR RECEIPTS / VIEWING)
     * =========================================================================
     * Purpose:
     * - Fetches main bill metadata from the 'bills' table using the specified bill ID.
     * - Joins 'bill_item' and 'inventory' tables to retrieve associated line items and medicine names.
     * - Returns the populated Bill object or null if not found.
     */
    public Bill getBillDetailsById(int billId) throws SQLException {
        String billSql = "SELECT bill_id, cashier_id, total_amount, discount_amount, net_total, is_prescription_checked, is_free_issue, create_at FROM bills WHERE bill_id = ?";
        Bill bill = null;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(billSql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bill = new Bill(
                        rs.getInt("bill_id"),
                        rs.getString("cashier_id"),
                        rs.getDouble("total_amount"),
                        rs.getDouble("discount_amount"),
                        "", 
                        rs.getDouble("net_total"),
                        rs.getBoolean("is_prescription_checked"),
                        rs.getBoolean("is_free_issue"),
                        rs.getTimestamp("create_at")
                    );
                }
            }
        }

        if (bill != null) {
            String itemsSql = "SELECT bi.item_id, bi.bill_id, bi.medicine_id, i.medicine_name, bi.sold_price, bi.quantity, bi.line_total " +
                              "FROM bill_item bi JOIN inventory i ON bi.medicine_id = i.medicine_id WHERE bi.bill_id = ?";
            List<BillItem> items = new ArrayList<>();

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setInt(1, billId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new BillItem(
                            rs.getInt("item_id"),
                            rs.getInt("bill_id"),
                            rs.getInt("medicine_id"),
                            rs.getString("medicine_name"),
                            rs.getDouble("sold_price"),
                            rs.getInt("quantity"),
                            rs.getDouble("line_total")
                        ));
                    }
                }
            }
            bill.setItems(items);
        }

        return bill;
    }

    /**
     * =========================================================================
     * HELPER METHODS
     * =========================================================================
     */
    // Checks if a bill ID is already recorded in the 'canceled_bills' table
    public boolean isBillCanceled(int billId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM canceled_bills WHERE bill_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // Private helper class for managing FEFO batch stock sorting and deduction
    private static class BatchStock {
        int medicineId;
        double unitPrice;
        int stockQuantity;
        Date expDate;

        public BatchStock(int medicineId, double unitPrice, int stockQuantity, Date expDate) {
            this.medicineId = medicineId;
            this.unitPrice = unitPrice;
            this.stockQuantity = stockQuantity;
            this.expDate = expDate;
        }
    }
}