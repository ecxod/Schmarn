package im.conversations.android.database.dao;

import static androidx.room.OnConflictStrategy.REPLACE;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.base.Strings;
import im.conversations.android.database.entity.RosterItemEntity;
import im.conversations.android.database.entity.RosterItemGroupEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.roster.Item;
import java.util.Collection;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dao
public abstract class RosterDao extends GroupDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(RosterDao.class);

    @Insert(onConflict = REPLACE)
    protected abstract long insert(RosterItemEntity rosterItem);

    @Query("DELETE FROM roster WHERE accountId=:account")
    protected abstract void clear(final long account);

    @Query("DELETE FROM roster WHERE accountId=:account AND address=:address")
    protected abstract void delete(final long account, final Jid address);

    @Query("UPDATE account SET rosterVersion=:version WHERE id=:account")
    protected abstract void setRosterVersion(final long account, final String version);

    @Query("SELECT EXISTS (SELECT id FROM roster WHERE accountId=:account AND address=:address)")
    public abstract boolean isInRoster(final long account, final BareJid address);

    @Transaction
    public void set(
            final Account account, final String version, final Collection<Item> rosterItems) {
        LOGGER.info("items: " + rosterItems);
        clear(account.id);
        for (final Item item : rosterItems) {
            final long id = insert(RosterItemEntity.of(account.id, item));
            insertRosterGroups(id, item.getGroups());
        }
        setRosterVersion(account.id, version);
        deleteEmptyGroups();
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
            insertRosterGroups(id, item.getGroups());
        }
        setRosterVersion(account.id, version);
        deleteEmptyGroups();
    }

    protected void insertRosterGroups(final long rosterItemId, Collection<String> groups) {
        for (final String group : groups) {
            if (Strings.isNullOrEmpty(group)) {
                continue;
            }
            insertRosterGroup(RosterItemGroupEntity.of(rosterItemId, getOrCreateId(group)));
        }
    }

    @Insert
    protected abstract void insertRosterGroup(RosterItemGroupEntity entity);
}
