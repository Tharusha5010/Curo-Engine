/*
 * ============================================================================
 * FILE: NotificationManager.java (Package: alerts)
 * ============================================================================
 * PURPOSE:
 * Manage all app notifications in one central place and update the UI 
 * automatically whenever changes happen.
 * 
 * ----------------------------------------------------------------------------
 * WHAT THIS FILE MUST DO:
 * ----------------------------------------------------------------------------
 * 1. SINGLE SHARED INSTANCE:
 *    - Ensure only one active manager exists across the whole application.
 *    - Make sure it works safely even if multiple background tasks access it 
 *      at the same time.
 * 
 * 2. SAFE DATA STORAGE:
 *    - Keep a list of all notifications and a list of active UI screens.
 *    - Storage must be thread-safe because alerts can be triggered from 
 *      background processes.
 * 
 * 3. UI SUBSCRIPTION SYSTEM:
 *    - Allow UI screens to subscribe (register) to receive live alert updates.
 *    - Avoid adding duplicate or empty subscriptions.
 *    - Allow UI screens to unsubscribe when they close (to prevent memory leaks).
 * 
 * 4. ADDING & BROADCASTING ALERTS:
 *    - Accept alert details (type, title, message), save the new alert, and 
 *      immediately inform all subscribed UI screens.
 *    - IMPORTANT: Make sure all UI updates run safely on the Swing UI thread 
 *      (Event Dispatch Thread) so the interface never freezes or crashes.
 * 
 * 5. READING & CLEARING ALERTS:
 *    - Provide a way to view all saved alerts (return a safe copy so external 
 *      code cannot accidentally modify the original list).
 *    - Provide the total count of alerts (for showing badge numbers on icons).
 *    - Provide a way to clear all alerts at once and inform the UI screens to 
 *      refresh (again, safely on the Swing UI thread).
 * ============================================================================
 */


package alerts;

import inventory.InventoryDAO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import models.Medicine;
import models.Notification;

public class NotificationManagaer {

    // Instantiate InventoryDAO to handle inventory operations
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    
    // In-memory list for runtime/session-based dynamic alerts
    private static final List<Notification> inMemoryAlerts = new ArrayList<>();

    public List<Notification> fetchLiveNotifications() {
        List<Notification> alerts = new ArrayList<>(inMemoryAlerts);

        try {
            // Process Low Stock items (getLowStockMedicines method)(threshold assign to 10 which can change.)
            List<Medicine> lowStockList = inventoryDAO.getLowStockMedicines(10);
            for (Medicine med : lowStockList) {
                String name = med.getMedicineName();
                String batch = med.getBatchNumber();
                int qty = med.getStockQuantity();

                if (inventoryDAO.isOutOfStock(med)) {
                    alerts.add(new Notification("OUT OF STOCK", name, "Batch " + batch + " is completely out of stock!"));
                } else if (qty < 10) {
                    alerts.add(new Notification("LOW STOCK", name, "Stock is low (" + qty + " units remaining)."));
                }
            }

            // Process Expiring Soon items (getExpiringSoonMedicines method) (7-month rule)
            List<Medicine> expiringSoonList = inventoryDAO.getExpiringSoonMedicines();
            for (Medicine med : expiringSoonList) {
                String name = med.getMedicineName();
                String batch = med.getBatchNumber();
                if (med.getExpDate() != null) {
                    LocalDate expDate = med.getExpDate().toLocalDate();
                    alerts.add(new Notification("EXPIRING SOON", name, "Batch " + batch + " expires on " + expDate + ". (Within 7 months)"));
                }
            }

            // Process Already Expired items (getExpiredMedicines method)
            List<Medicine> expiredList = inventoryDAO.getExpiredMedicines();
            for (Medicine med : expiredList) {
                String name = med.getMedicineName();
                String batch = med.getBatchNumber();
                if (med.getExpDate() != null) {
                    LocalDate expDate = med.getExpDate().toLocalDate();
                    alerts.add(new Notification("EXPIRED", name, "Batch " + batch + " expired on " + expDate + "!"));
                }
            }

        } catch (Exception e) {
            System.err.println("NotificationManager Error Fetching Live Alerts: " + e.getMessage());
        }

        return alerts;
    }

    //Adds a dynamic runtime alert (e.g., login success, stock added) to session memory.
    public static void addDynamicAlert(String type, String title, String message) {
        inMemoryAlerts.add(0, new Notification(type, title, message));
    }

    //Helper method to evaluate a single medicine object instantly in memory 
    //(zero database overhead for fast UI table row checks).
    public void evaluateSingleItem(Medicine med) {
        if (med == null) {
            return;
        }

        if (inventoryDAO.isOutOfStock(med)) {
            addDynamicAlert("OUT OF STOCK", med.getMedicineName(), "Item is currently out of stock.");
        }
        
        else if (inventoryDAO.isExpired(med)) {
            addDynamicAlert("EXPIRED", med.getMedicineName(), "Batch " + med.getBatchNumber() + " is expired!");
        }
        
        else if (inventoryDAO.isExpiringSoon(med)) {
            addDynamicAlert("EXPIRING SOON", med.getMedicineName(), "Batch " + med.getBatchNumber() + " expires within 7 months.");
        }
    }
}