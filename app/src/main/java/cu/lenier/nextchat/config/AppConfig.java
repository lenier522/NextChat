package cu.lenier.nextchat.config;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import cu.lenier.nextchat.service.MailService;
import cu.lenier.nextchat.work.MailSyncWorker;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AppConfig extends Application {
    private static AppConfig instance;
    private static final String CHANNEL_SYNC    = "MailSyncChannel";
    private static final String CHANNEL_NEWMSG  = "NewMsgChannel";

    /** Mapa inmutable de correos verificados → nombre a mostrar */
    private static final Map<String,String> VERIFIED_NAMES;
    static {
        Map<String,String> m = new HashMap<>();
        m.put("leniercruz02@nauta.cu", "Lenier");
        // … añade aquí más pares correo→nombre …
        VERIFIED_NAMES = Collections.unmodifiableMap(m);
    }

    private static String currentChatContact = null;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
        scheduleWorkerIfNeeded();

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
                    CHANNEL_SYNC,   "NextChat Sync",    NotificationManager.IMPORTANCE_LOW
            ));
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_NEWMSG, "Notificaciones",   NotificationManager.IMPORTANCE_DEFAULT
            ));
        }
    }

    private void scheduleWorkerIfNeeded() {
        String email = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("email", "");
        String pass  = getSharedPreferences("prefs", MODE_PRIVATE)
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

    /** Devuelve true si el correo está verificado */
    public static boolean isVerified(String email) {
        return email != null && VERIFIED_NAMES.containsKey(email.trim().toLowerCase());
    }

    /** Nombre a mostrar: si está verificado, su alias; si no, el mismo correo */
    public static String getDisplayName(String email) {
        if (email == null) return "";
        String key = email.trim().toLowerCase();
        return VERIFIED_NAMES.getOrDefault(key, email);
    }
}
