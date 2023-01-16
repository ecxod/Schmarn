package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Transaction;
import androidx.room.Upsert;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.DiscoItemEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.disco.items.Item;
import java.util.Collection;

@Dao
public abstract class DiscoDao {

    @Upsert(entity = DiscoItemEntity.class)
    protected abstract void setDiscoItems(Collection<DiscoItemWithParent> items);

    @Transaction
    public void setDiscoItems(
            final Account account, final Jid parent, final Collection<Item> items) {
        final var entities =
                Collections2.transform(items, i -> DiscoItemWithParent.of(account.id, parent, i));
        setDiscoItems(entities);
    }

    public static class DiscoItemWithParent {
        public long accountId;
        public Jid address;
        public String node;
        public Jid parent;

        public static DiscoItemWithParent of(
                final long account, final Jid parent, final Item item) {
            final var entity = new DiscoItemWithParent();
            entity.accountId = account;
            entity.address = item.getJid();
            entity.node = item.getNode();
            entity.parent = parent;
            return entity;
        }
    }
}
