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


