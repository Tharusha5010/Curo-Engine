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
    

    private final InventoryDAO inventoryDAO = new InventoryDAO();


    
    //Check and return database connection
    private Connection getValidConnection() throws SQLException {
        Connection conn = DBConnection.getConnection();
        if (conn == null) {
            throw new SQLException("Database connection is null!");
        }
        return conn;
    }


    
    //Create a new bill and deduct stock using FEFO
    public int createBill(Bill bill) throws UnauthorizedAccessException, OutOfStockException, ExpiredMedicineException, SQLException {
        if (bill == null || bill.getItems() == null || bill.getItems().isEmpty()) {
            throw new IllegalArgumentException("Bill must contain at least one item.");
        }
        

        //Only active owners can create free issue bills
        if (bill.isFreeIssue()) {
            StaffMember currentUser = UserSession.getCurrentUser();
            if (currentUser == null || !"OWNER".equalsIgnoreCase(currentUser.getRole())) {
                throw new UnauthorizedAccessException("Unauthorized: Only an active Owner can authorize Free Issue bills.");
            }
        }

        
        //Combine quantities if same medicine ID appears multiple times
        Map<Integer, Integer> mergedItemsMap = new HashMap<>();
        Map<Integer, String> medicineNamesMap = new HashMap<>();
        for (BillItem item : bill.getItems()) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than 0 for all items.");
            }
            mergedItemsMap.put(item.getMedicineId(), mergedItemsMap.getOrDefault(item.getMedicineId(), 0) + item.getQuantity());
            medicineNamesMap.put(item.getMedicineId(), item.getMedicineName());
        }
        

        Connection conn = null;
        int generatedBillId = -1;
        List<BillItem> finalizedBatchItems = new ArrayList<>();

        
        try {
            conn = getValidConnection();
            conn.setAutoCommit(false); // Start database transaction

            double calculatedTotal = 0.0;

            for (Map.Entry<Integer, Integer> entry : mergedItemsMap.entrySet()) {
                int medicineId = entry.getKey();
                int remainingQtyNeeded = entry.getValue();
                String medName = medicineNamesMap.get(medicineId);

                //Check medicine name and prescription requirement
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

                
                //Stop non-owners if prescription is required but not checked
                if (requiresRx && !bill.isPrescriptionChecked()) {
                    StaffMember currentUser = UserSession.getCurrentUser();
                    boolean isOwner = currentUser != null && "OWNER".equalsIgnoreCase(currentUser.getRole());
                    if (!isOwner) {
                        conn.rollback();
                        throw new UnauthorizedAccessException("Prescription verification required for medicine: " + medName);
                    }
                }

                
                //Get active batches sorted by earliest expiry date
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
                

                //Check if medicine is expired or out of stock
                if (activeBatches.isEmpty()) {
                    String expiredCheckSql = "SELECT COUNT(*) FROM inventory WHERE medicine_id = ? AND stock_quantity > 0 AND exp_date <= GETDATE()";
                    try (PreparedStatement expStmt = conn.prepareStatement(expiredCheckSql)) {
                        expStmt.setInt(1, medicineId);
                        try (ResultSet expRs = expStmt.executeQuery()) {
                            if (expRs.next() && expRs.getInt(1) > 0) {
                                conn.rollback();
                                throw new ExpiredMedicineException("Medicine '" + medName + "' is expired and cannot be billed!");
                            }
                        }
                    }
                    
                    conn.rollback();
                    throw new OutOfStockException("Medicine '" + medName + "' is currently out of stock!");
                }

                
                //Check if total available stock is enough
                if (totalAvailableStock < remainingQtyNeeded) {
                    conn.rollback();
                    throw new OutOfStockException(
                        "Insufficient stock for '" + medName + "'. Available: " + totalAvailableStock + ", Requested: " + remainingQtyNeeded
                    );
                }

                
                //Deduct stock starting from earliest expiring batch
                for (BatchStock batch : activeBatches) {
                    if (remainingQtyNeeded <= 0) break;

                    int deductFromThisBatch = Math.min(batch.stockQuantity, remainingQtyNeeded);
                    double unitPrice = batch.unitPrice;
                    double lineTotal = bill.isFreeIssue() ? 0.0 : (unitPrice * deductFromThisBatch);

                    calculatedTotal += lineTotal;

                    //Update batch stock in database
                    String updateStockSql = "UPDATE inventory SET stock_quantity = stock_quantity - ? WHERE medicine_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateStockSql)) {
                        updateStmt.setInt(1, deductFromThisBatch);
                        updateStmt.setInt(2, batch.medicineId);
                        updateStmt.executeUpdate();
                    }
                    

                    BillItem batchLineItem = new BillItem(
                        batch.medicineId,
                        medName,
                        unitPrice,
                        deductFromThisBatch
                    );
                    batchLineItem.setLineTotal(lineTotal);
                    finalizedBatchItems.add(batchLineItem);

                    remainingQtyNeeded -= deductFromThisBatch;
                }
            }
            

            //Calculate 5% discount and final net total
            double discountAmount = 0.0;
            double netTotal = calculatedTotal;

            
            if (bill.isFreeIssue()) {
                discountAmount = calculatedTotal;
                netTotal = 0.0;
                bill.setDiscountReason("Free Issue (Owner Authorized)");
            } else {
                String reason = bill.getDiscountReason();
                if (reason != null && !reason.trim().isEmpty() && !"None".equalsIgnoreCase(reason.trim())) {
                    discountAmount = Math.round((calculatedTotal * 0.05) * 100.0) / 100.0;
                    netTotal = Math.max(0.0, calculatedTotal - discountAmount);
                    bill.setDiscountReason(reason.trim());
                } else {
                    bill.setDiscountReason("None");
                }
            }

            
            bill.setTotalAmount(calculatedTotal);
            bill.setDiscountAmount(discountAmount);
            bill.setNetTotal(netTotal);

            
            //Save bill record and get new bill ID
            String insertBillSql = "INSERT INTO bills (cashier_id, total_amount, discount_amount, net_total, is_prescription_checked, is_free_issue, create_at) " +
                                   "VALUES (?, ?, ?, ?, ?, ?, GETDATE())";

            
            try (PreparedStatement billStmt = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {
                String cashierId = (UserSession.getCurrentUser() != null) ? UserSession.getCurrentUser().getStaffId() : bill.getCashierId();
                billStmt.setString(1, cashierId);
                billStmt.setDouble(2, calculatedTotal);
                billStmt.setDouble(3, discountAmount);
                billStmt.setDouble(4, netTotal);
                billStmt.setBoolean(5, bill.isPrescriptionChecked());
                billStmt.setBoolean(6, bill.isFreeIssue());

                billStmt.executeUpdate();

                
                try (ResultSet keys = billStmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        generatedBillId = keys.getInt(1);
                        bill.setBillId(generatedBillId);
                    } else {
                        throw new SQLException("Failed to retrieve generated Bill ID.");
                    }
                }
            }

            
            //Save all bill items to database
            String insertItemSql = "INSERT INTO bill_item (bill_id, medicine_id, sold_price, quantity, line_total) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql)) {
                for (BillItem item : finalizedBatchItems) {
                    itemStmt.setInt(1, generatedBillId);
                    itemStmt.setInt(2, item.getMedicineId());
                    itemStmt.setDouble(3, item.getSoldPrice());
                    itemStmt.setInt(4, item.getQuantity());
                    itemStmt.setDouble(5, item.getLineTotal());
                    itemStmt.addBatch();
                }
                
                itemStmt.executeBatch();
            }

            
            conn.commit(); //Save all changes permanently
            bill.setItems(finalizedBatchItems);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback(); //Undo all changes on error
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }

        
        //Update moving types and check low stock alerts after billing
        try {
            inventoryDAO.autoUpdateMovingTypes(); //Update FAST/SLOW tags from 7-day sales
            for (BillItem item : finalizedBatchItems) {
                Medicine med = inventoryDAO.getMedicineById(item.getMedicineId());
                if (med != null) {
                    inventoryDAO.evaluateTotalStockAlert(med.getMedicineName(), med.getMovingType()); //Check if stock dropped below 50%
                }
            }
        } catch (Exception alertEx) {
            System.err.println("Warning: Failed to update moving types or evaluate low stock alert after billing: " + alertEx.getMessage());
        }

    
        return generatedBillId;
    }


    
    //Cancel a bill and restore stock (Owner only)
    public boolean cancelBill(int billId, String reason) throws UnauthorizedAccessException, SQLException, IllegalStateException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Only an Owner is authorized to cancel bills!");
        }

        if (isBillCanceled(billId)) {
            throw new IllegalStateException("Bill #" + billId + " has already been canceled.");
        }

        Connection conn = null;
        StaffMember currentOwner = UserSession.getCurrentUser();
        String ownerId = (currentOwner != null) ? currentOwner.getStaffId() : "OWNER";
        String safeReason = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Canceled by Owner";
        if (safeReason.length() > 50) {
            safeReason = safeReason.substring(0, 50);
        }

        
        try {
            conn = getValidConnection();
            conn.setAutoCommit(false);

            //Get all items from the bill
            String fetchItemsSql = "SELECT medicine_id, quantity FROM bill_item WHERE bill_id = ?";
            List<BillItem> itemsToRestore = new ArrayList<>();

            try (PreparedStatement fetchStmt = conn.prepareStatement(fetchItemsSql)) {
                fetchStmt.setInt(1, billId);
                try (ResultSet rs = fetchStmt.executeQuery()) {
                    while (rs.next()) {
                        BillItem item = new BillItem();
                        item.setMedicineId(rs.getInt("medicine_id"));
                        item.setQuantity(rs.getInt("quantity"));
                        itemsToRestore.add(item);
                    }
                }
            }

            
            if (itemsToRestore.isEmpty()) {
                conn.rollback();
                throw new IllegalArgumentException("Bill #" + billId + " not found or has no items.");
            }
            

            //Add sold quantities back to inventory stock
            String restoreStockSql = "UPDATE inventory SET stock_quantity = stock_quantity + ? WHERE medicine_id = ?";
            try (PreparedStatement restoreStmt = conn.prepareStatement(restoreStockSql)) {
                for (BillItem item : itemsToRestore) {
                    restoreStmt.setInt(1, item.getQuantity());
                    restoreStmt.setInt(2, item.getMedicineId());
                    restoreStmt.executeUpdate();
                }
            }

            
            //Save cancel record to canceled_bills table
            String insertCancelSql = "INSERT INTO canceled_bills (bill_id, canceled_by, canceled_at, reason) VALUES (?, ?, GETDATE(), ?)";
            try (PreparedStatement cancelStmt = conn.prepareStatement(insertCancelSql)) {
                cancelStmt.setInt(1, billId);
                cancelStmt.setString(2, ownerId);
                cancelStmt.setString(3, safeReason);
                cancelStmt.executeUpdate();
            }

            conn.commit();
            

            //Send bill canceled alert
            try {
                NotificationManager.getInstance().addNotification(
                    "BILL_CANCELED",
                    "Bill #" + billId + " Canceled",
                    "Canceled by Owner (" + ownerId + "). Stock restored. Reason: " + safeReason
                );
            } catch (Exception notifEx) {
                System.err.println("Warning: Failed to send cancel notification: " + notifEx.getMessage());
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

    

    //Get all canceled bills (Owner only)
    public List<CanceledBill> getAllCanceledBills() throws UnauthorizedAccessException, SQLException {
        if (!UserSession.isOwner()) {
            throw new UnauthorizedAccessException("Only an Owner can view canceled bills.");
        }

        List<CanceledBill> canceledList = new ArrayList<>();
        String sql = "SELECT cancel_id, bill_id, canceled_by, canceled_at, reason FROM canceled_bills ORDER BY canceled_at DESC";

        
        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

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


    
    //Get bill details and items by bill ID
    public Bill getBillById(int billId) throws SQLException {
        String billSql = "SELECT * FROM bills WHERE bill_id = ?";
        Bill bill = null;

        try (Connection conn = getValidConnection();
             PreparedStatement billStmt = conn.prepareStatement(billSql)) {

            billStmt.setInt(1, billId);
            try (ResultSet rs = billStmt.executeQuery()) {
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

            try (Connection conn = getValidConnection();
                 PreparedStatement itemStmt = conn.prepareStatement(itemsSql)) {
                itemStmt.setInt(1, billId);
                try (ResultSet itemRs = itemStmt.executeQuery()) {
                    while (itemRs.next()) {
                        items.add(new BillItem(
                            itemRs.getInt("item_id"),
                            itemRs.getInt("bill_id"),
                            itemRs.getInt("medicine_id"),
                            itemRs.getString("medicine_name"),
                            itemRs.getDouble("sold_price"),
                            itemRs.getInt("quantity"),
                            itemRs.getDouble("line_total")
                        ));
                    }
                }
            }
            
            bill.setItems(items);
        }
        
        return bill;
    }

    

    //Check if a bill is already canceled
    public boolean isBillCanceled(int billId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM canceled_bills WHERE bill_id = ?";
        try (Connection conn = getValidConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, billId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
    

    //Batch stock sorting (Helper)
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
