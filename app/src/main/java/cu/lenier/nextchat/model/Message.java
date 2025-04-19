package cu.lenier.nextchat.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {
    @PrimaryKey(autoGenerate = true) public int id;
    public String fromAddress;
    public String toAddress;
    public String subject;        // "NextChat" o "NextChat Audio"
    public String body;           // texto plano
    public String attachmentPath; // ruta local si es audio
    public long timestamp;
    public boolean sent;          // true si lo enviaste tú
    public boolean read;          // true si ya lo leíste
    public String type;           // "text" o "audio"
}
