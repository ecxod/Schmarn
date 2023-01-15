package im.conversations.android.database.dao;

import static androidx.room.OnConflictStrategy.REPLACE;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.RosterItemEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.roster.Item;
import java.util.Collection;

@Dao
public abstract class RosterDao {

    @Insert(onConflict = REPLACE)
    protected abstract long insert(RosterItemEntity rosterItem);

    @Query("DELETE FROM roster WHERE accountId=:account")
    protected abstract void clear(final long account);

    @Query("DELETE FROM roster WHERE accountId=:account AND address=:address")
    protected abstract void delete(final long account, final Jid address);

    @Query("UPDATE account SET rosterVersion=:version WHERE id=:account")
    protected abstract void setRosterVersion(final long account, final String version);

    @Transaction
    public void set(
            final Account account, final String version, final Collection<Item> rosterItems) {
        clear(account.id);
        for (final Item item : rosterItems) {
            final long id = insert(RosterItemEntity.of(account.id, item));
            // TODO insert groups
        }
        setRosterVersion(account.id, version);
    }

    public void update(
            final Account account, final String version, final Collection<Item> updates) {
        for (final Item item : updates) {
            final Item.Subscription subscription = item.getSubscription();
            if (subscription == null) {
                continue;
            }
            if (subscription == Item.Subscription.REMOVE) {
                delete(account.id, item.getJid());
            }
            final RosterItemEntity entity = RosterItemEntity.of(account.id, item);
            final long id = insert(entity);
        }
        setRosterVersion(account.id, version);
    }
}
