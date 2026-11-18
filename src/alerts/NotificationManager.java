package alerts;



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;
import models.Notification;



public class NotificationManager {

    
    //Single shared instance
    private static volatile NotificationManager instance;

    
    //Thread-safe lists for alerts and UI screens
    private final List<Notification> notifications;
    private final List<Runnable> listeners;

    
    //Private constructor
    private NotificationManager() {
        this.notifications = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }


    //Get the single instance safely

    @SuppressWarnings("DoubleCheckedLocking")
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


    //Add a UI screen to get live updates
    public void subscribe(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }


    
    //Remove UI screen when closed
    public void unsubscribe(Runnable listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }


    //Add a new alert and update all screens
    public void addNotification(String type, String title, String message) {
        // Remove old alert if same type and title already exist
        for (int i = 0; i < notifications.size(); i++) {
            Notification existing = notifications.get(i);
            if (existing.getType() != null && existing.getTitle() != null &&
                existing.getType().equalsIgnoreCase(type) &&
                existing.getTitle().equalsIgnoreCase(title)) {
                notifications.remove(i);
                break;
            }
        }

        
        //Add newest alert to the top
        Notification newNotification = new Notification(type, title, message);
        notifications.add(0, newNotification);
        notifySubscribers();
    }

    

    //Update UI safely on Swing thread
    private void notifySubscribers() {
        for (Runnable listener : listeners) {
            SwingUtilities.invokeLater(listener);
        }
    }


    
    //Return a safe copy of all alerts
    public List<Notification> getAllAlerts() {
        return new ArrayList<>(notifications);
    }


    //Get total alert count
    public int getAlertCount() {
        return notifications.size();
    }

    

    //Remove one alert by index
    public void removeAlert(int index) {
        if (index >= 0 && index < notifications.size()) {
            notifications.remove(index);
            notifySubscribers();
        }
    }

    //Remove one specific alert
    public void removeAlert(Notification target) {
        if (target != null && notifications.remove(target)) {
            notifySubscribers();
        }
    }


    
    //Remove all alerts
    public void clearAllAlerts() {
        notifications.clear();
        notifySubscribers();
    }


    
    //Low or out of stock alerts  (Helper)
    public void notifyLowStock(String medicineName, int remainingQty) {
        if (remainingQty <= 0) {
            addNotification("OUT OF STOCK", medicineName, "Completely out of stock! Immediate restock required.");
        } else {
            addNotification("LOW STOCK", medicineName, "Low stock warning: Only " + remainingQty + " units remaining.");
        }
    }


    //Expired or expiring alerts (Helper)
    //new feature
    public void notifyExpiringSoon(String medicineName, String expDate, boolean isAlreadyExpired) {
        if (isAlreadyExpired) {
            addNotification("EXPIRED", medicineName, "Medicine expired on " + expDate + ". Do not issue!");
        } else {
            addNotification("EXPIRING SOON", medicineName, "Expiring within 7 months (Exp: " + expDate + ").");
        }
    }


    //Canceled bill alerts (Helper)
    public void notifyBillCanceled(int billId, String reason) {
        addNotification("WARNING", "Bill #" + billId + " Canceled", "Reason: " + reason);
    }

    // Helper for unauthorized access Alerts
    public void notifyUnauthorizedAccess(String staffId, String actionName) {
        addNotification("SECURITY", "Unauthorized Access", "Staff [" + staffId + "] attempted: " + actionName);
    }
}
