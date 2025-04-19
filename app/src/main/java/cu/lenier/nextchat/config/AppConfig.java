package cu.lenier.nextchat.config;

import android.app.Application;

public class AppConfig extends Application {
    private static AppConfig instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static AppConfig getInstance() {
        return instance;
    }
}
