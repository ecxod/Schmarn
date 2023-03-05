package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.ChatType;
import im.conversations.android.database.model.MucState;

@Entity(
        tableName = "chat",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "address"},
                    unique = true)
        })
public class ChatEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public String address;

    @NonNull public ChatType type;

    public boolean archived;

    @Nullable public MucState mucState;
    @Nullable public String errorCondition;
}
