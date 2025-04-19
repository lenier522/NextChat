// src/main/java/cu/lenier/nextchat/model/Message.java
package cu.lenier.nextchat.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String fromAddress;
    public String toAddress;
    public String subject;
    public String body;
    public long timestamp;
    public boolean sent;
    public boolean read;
}
