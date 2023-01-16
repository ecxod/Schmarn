package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;

@Entity(
        tableName = "disco_item",
        foreignKeys = {
            @ForeignKey(
                    entity = AccountEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"accountId"},
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(
                    entity = DiscoEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"discoId"},
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(
                    value = {"accountId", "address"},
                    unique = true),
            @Index(
                    value = {"accountId", "parent"},
                    unique = false)
        })
public class DiscoItemEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull Long accountId;

    @NonNull Jid address;

    @Nullable public Jid parent;

    public Long discoId;
}
