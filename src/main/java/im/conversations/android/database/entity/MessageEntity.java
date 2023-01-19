package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.Instant;

@Entity(
        tableName = "message",
        foreignKeys =
                @ForeignKey(
                        entity = ChatEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"chatId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "chatId")})
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long chatId;

    public Instant receivedAt;
    public Instant sentAt;

    public String bareTo;
    public String toResource;
    public String bareFrom;
    public String fromResource;

    public String occupantId;

    public String messageId;
    public String stanzaId;

    public boolean acknowledged = false;
}