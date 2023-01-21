package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.disco.items.Item;

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
                    value = {"accountId", "address", "node", "parentAddress", "parentNode"},
                    unique = true),
            @Index(
                    value = {"accountId", "parentAddress"},
                    unique = false),
            @Index(value = {"discoId"})
        })
public class DiscoItemEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Jid address;

    @NonNull public String node;

    @NonNull public String parentAddress;

    @NonNull public String parentNode;

    public Long discoId;

    public static DiscoItemEntity of(
            long accountId, final Jid parent, final String parentNode, final Item item) {
        final var entity = new DiscoItemEntity();
        entity.accountId = accountId;
        entity.address = item.getJid();
        entity.node = Strings.nullToEmpty(item.getNode());
        entity.parentAddress = parent.toEscapedString();
        entity.parentNode = Strings.nullToEmpty(parentNode);
        return entity;
    }

    public static DiscoItemEntity of(
            long accountId, final Jid address, final String node, final long discoId) {
        final var entity = new DiscoItemEntity();
        entity.accountId = accountId;
        entity.address = address;
        entity.node = Strings.nullToEmpty(node);
        entity.parentAddress = "";
        entity.discoId = discoId;
        return entity;
    }
}
