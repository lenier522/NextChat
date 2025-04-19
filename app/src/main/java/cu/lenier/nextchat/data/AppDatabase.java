package cu.lenier.nextchat.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import cu.lenier.nextchat.model.Message;

@Database(entities = {Message.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MessageDao messageDao();

    private static volatile AppDatabase INSTANCE;
    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                INSTANCE = Room.databaseBuilder(
                        ctx.getApplicationContext(),
                        AppDatabase.class,
                        "mailchat_db"
                ).build();
            }
        }
        return INSTANCE;
    }
}
