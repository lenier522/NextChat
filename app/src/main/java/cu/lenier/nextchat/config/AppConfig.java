package cu.lenier.nextchat.config;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import cu.lenier.nextchat.service.MailService;
import cu.lenier.nextchat.work.MailSyncWorker;

public class AppConfig extends Application {
    private static AppConfig instance;
    private static final String CHANNEL_SYNC   = "MailSyncChannel";
    private static final String CHANNEL_NEWMSG = "NewMsgChannel";

    private static String currentChatContact = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        createNotificationChannels();
        scheduleWorkerIfNeeded();

        // Arranca el MailService en primer o segundo plano
        Intent svc = new Intent(this, MailService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_SYNC,    "NextChat Sync",    NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_NEWMSG,  "Notificaciones de NextChat",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private void scheduleWorkerIfNeeded() {
        // Mantén tu MailSyncWorker para reinicios, pero ya no dependes solo de él
        String email = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getString("email", "");
        String pass  = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getString("pass", "");
        if (!email.isEmpty() && !pass.isEmpty()) {
            MailSyncWorker.schedulePeriodicSync(this);
        }
    }

    public static AppConfig getInstance() {
        return instance;
    }

    public static void setCurrentChat(String contact) {
        currentChatContact = contact;
    }
    public static String getCurrentChat() {
        return currentChatContact;
    }
}
