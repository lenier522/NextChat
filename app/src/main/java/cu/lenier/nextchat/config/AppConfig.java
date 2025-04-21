package cu.lenier.nextchat.config;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import cu.lenier.nextchat.work.MailSyncWorker;

public class AppConfig extends Application {
    private static AppConfig instance;
    private static final String CHANNEL_SYNC   = "MailSyncChannel";
    private static final String CHANNEL_NEWMSG = "NewMsgChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();

        // Programar sincronización periódica si está logueado
        SharedPreferences prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);
        String email = prefs.getString("email", "");
        String pass  = prefs.getString("pass", "");
        if (!email.isEmpty() && !pass.isEmpty()) {
            MailSyncWorker.schedulePeriodicSync(this);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel syncChannel = new NotificationChannel(
                    CHANNEL_SYNC,
                    "NextChat Sync",
                    NotificationManager.IMPORTANCE_LOW
            );
            syncChannel.setDescription("Sincronización de correo IMAP en segundo plano");

            NotificationChannel newMsgChannel = new NotificationChannel(
                    CHANNEL_NEWMSG,
                    "Notificaciones de NextChat",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            newMsgChannel.setDescription("Alertas de mensajes nuevos");

            nm.createNotificationChannel(syncChannel);
            nm.createNotificationChannel(newMsgChannel);
        }
    }

    public static AppConfig getInstance() {
        return instance;
    }
}
