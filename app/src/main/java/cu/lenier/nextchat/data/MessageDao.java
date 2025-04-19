package cu.lenier.nextchat.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.model.UnreadCount;

@Dao
public interface MessageDao {
    @Insert void insert(Message msg);

    @Query("SELECT * FROM messages " +
            "WHERE subject IN ('NextChat','NextChat Audio') " +
            "  AND (fromAddress = :addr OR toAddress = :addr) " +
            "ORDER BY timestamp ASC")
    LiveData<List<Message>> getByContact(String addr);

    @Query("SELECT DISTINCT CASE WHEN sent=1 THEN toAddress ELSE fromAddress END " +
            "FROM messages " +
            "WHERE subject IN ('NextChat','NextChat Audio')")
    LiveData<List<String>> getContacts();

    @Query("SELECT CASE WHEN sent=1 THEN toAddress ELSE fromAddress END AS contact, " +
            "COUNT(*) AS unread " +
            "FROM messages " +
            "WHERE subject IN ('NextChat','NextChat Audio') " +
            "  AND sent=0 AND read=0 " +
            "GROUP BY contact")
    LiveData<List<UnreadCount>> getUnreadCounts();

    @Query("UPDATE messages SET read=1 " +
            "WHERE subject IN ('NextChat','NextChat Audio') " +
            "  AND fromAddress=:contact AND toAddress=:me AND read=0")
    void markAsRead(String contact, String me);

    @Query("DELETE FROM messages " +
            "WHERE subject IN ('NextChat','NextChat Audio') " +
            "  AND (fromAddress=:contact OR toAddress=:contact)")
    void deleteByContact(String contact);
}
