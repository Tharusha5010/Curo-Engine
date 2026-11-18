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


