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


