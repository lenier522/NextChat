// src/main/java/cu/lenier/nextchat/config/AppConfig.java
package cu.lenier.nextchat.config;

import android.app.Application;
import android.content.Intent;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.service.MailService;

public class AppConfig extends Application {
    private static AppConfig instance;
    private MessageDao messageDao;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Inicializar Room
        AppDatabase db = AppDatabase.getInstance(this);
        messageDao = db.messageDao();
        // Arrancar recepción de correos
        startService(new Intent(this, MailService.class));
    }

    public static AppConfig getInstance() {
        return instance;
    }

    public MessageDao getMessageDao() {
        return messageDao;
    }
}
