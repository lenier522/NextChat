package cu.lenier.nextchat.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import cu.lenier.nextchat.model.Message;

@Database(entities = {Message.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MessageDao messageDao();

    private static volatile AppDatabase INSTANCE;

    // Migration de v1 → v3: añadimos attachmentPath, type y sendState
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE messages ADD COLUMN attachmentPath TEXT");
            db.execSQL("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'text'");
            db.execSQL("ALTER TABLE messages ADD COLUMN sendState INTEGER NOT NULL DEFAULT 2");
        }
    };

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "mailchat_db"
                            )
                            .addMigrations(MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
