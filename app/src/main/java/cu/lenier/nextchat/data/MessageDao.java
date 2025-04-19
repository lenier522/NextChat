// src/main/java/cu/lenier/nextchat/data/MessageDao.java
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

    // Hilo de un contacto
    @Query("SELECT * FROM messages WHERE (fromAddress = :addr OR toAddress = :addr) AND subject = 'NextChat' ORDER BY timestamp ASC")
    LiveData<List<Message>> getByContact(String addr);

    // Lista de contactos con los que has intercambiado NextChat
    @Query("SELECT DISTINCT CASE WHEN sent = 1 THEN toAddress ELSE fromAddress END " +
            "FROM messages WHERE subject = 'NextChat'")
    LiveData<List<String>> getContacts();

    // Conteo de no leídos
    @Query("SELECT CASE WHEN sent = 1 THEN toAddress ELSE fromAddress END AS contact, " +
            "COUNT(*) AS unread " +
            "FROM messages " +
            "WHERE sent = 0 AND read = 0 AND subject = 'NextChat' " +
            "GROUP BY contact")
    LiveData<List<UnreadCount>> getUnreadCounts();

    // Marcar como leídos
    @Query("UPDATE messages SET read = 1 WHERE fromAddress = :contact AND toAddress = :me AND subject = 'NextChat' AND read = 0")
    void markAsRead(String contact, String me);

    // Eliminar chat completo
    @Query("DELETE FROM messages WHERE (fromAddress = :contact OR toAddress = :contact) AND subject = 'NextChat'")
    void deleteByContact(String contact);
}
