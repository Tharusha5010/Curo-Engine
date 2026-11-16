package alerts;

import models.Notification;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager {

    // 1. SINGLE SHARED INSTANCE (Thread-safe Singleton)
    private static volatile NotificationManager instance;

    // 2. SAFE DATA STORAGE (Thread-safe lists in RAM)
    private final List<Notification> notifications;
    private final List<Runnable> listeners;

    // Private constructor to prevent external instantiation
    private NotificationManager() {
        this.notifications = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    // Double-checked locking for thread-safe global access
    public static NotificationManager getInstance() {
        if (instance == null) {
            synchronized (NotificationManager.class) {
                if (instance == null) {
                    instance = new NotificationManager();
                }
            }
        }
        return instance;
    }

    // 3. UI SUBSCRIPTION SYSTEM
    public void subscribe(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(Runnable listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    // 4. ADDING & BROADCASTING ALERTS (Renamed to addNotification as requested)
    public void addNotification(String type, String title, String message) {
        // Prevent duplicate alerts with the same type & title; update existing one instead
        for (int i = 0; i < notifications.size(); i++) {
            Notification existing = notifications.get(i);
            if (existing.getType() != null && existing.getTitle() != null &&
                existing.getType().equalsIgnoreCase(type) &&
                existing.getTitle().equalsIgnoreCase(title)) {
                notifications.remove(i);
                break;
            }
        }

        Notification newNotification = new Notification(type, title, message);
        notifications.add(0, newNotification); // Add newest/updated alert at the top
        notifySubscribers();
    }

    // Helper method to safely update all subscribed UI screens on the Swing EDT
    private void notifySubscribers() {
        for (Runnable listener : listeners) {
            SwingUtilities.invokeLater(listener);
        }
    }

    // 5. READING & CLEARING ALERTS
    public List<Notification> getAllAlerts() {
        // Return a safe copy so external code cannot accidentally modify the original list
        return new ArrayList<>(notifications);
    }

    public int getAlertCount() {
        return notifications.size();
    }

    public void removeAlert(int index) {
        if (index >= 0 && index < notifications.size()) {
            notifications.remove(index);
            notifySubscribers();
        }
    }

    public void removeAlert(Notification target) {
        if (target != null && notifications.remove(target)) {
            notifySubscribers();
        }
    }

    public void clearAllAlerts() {
        notifications.clear();
        notifySubscribers();
    }

    // 6. MODULE INTEGRATION SHORTCUT HELPERS (For Auth, Inventory & Billing)
    public void notifyLowStock(String medicineName, int remainingQty) {
        if (remainingQty <= 0) {
            addNotification("OUT OF STOCK", medicineName, "Completely out of stock! Immediate restock required.");
        } else {
            addNotification("LOW STOCK", medicineName, "Low stock warning: Only " + remainingQty + " units remaining.");
        }
    }

    public void notifyExpiringSoon(String medicineName, String expDate, boolean isAlreadyExpired) {
        if (isAlreadyExpired) {
            addNotification("EXPIRED", medicineName, "Medicine expired on " + expDate + ". Do not issue!");
        } else {
            addNotification("EXPIRING SOON", medicineName, "Expiring soon on " + expDate + ".");
        }
    }

    public void notifyBillCanceled(int billId, String reason) {
        addNotification("WARNING", "Bill #" + billId + " Canceled", "Reason: " + reason);
    }

    public void notifyUnauthorizedAccess(String staffId, String actionName) {
        addNotification("SECURITY", "Unauthorized Access", "Staff [" + staffId + "] attempted: " + actionName);
    }
}