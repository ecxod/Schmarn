package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.roster.Item;
import java.util.Collection;

@Entity(
        tableName = "roster",
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
public class RosterItemEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Jid address;

    public Item.Subscription subscription;

    public boolean isPendingOut;

    public String name;

    public static RosterItemEntity of(final long accountId, final Item item) {
        final var entity = new RosterItemEntity();
        entity.accountId = accountId;
        entity.address = item.getJid();
        entity.subscription = item.getSubscription();
        entity.isPendingOut = item.isPendingOut();
        entity.name = item.getItemName();
        return entity;
    }

    public static Collection<RosterItemEntity> of(
            final long accountId, final Collection<Item> items) {
        return Collections2.transform(items, i -> of(accountId, i));
    }
}
