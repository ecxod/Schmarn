package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import im.conversations.android.database.entity.RosterItemEntity;
import im.conversations.android.database.model.Account;
import java.util.Collection;

@Dao
public abstract class RosterDao {

    @Insert
    protected abstract void insert(Collection<RosterItemEntity> rosterItems);

    @Query("DELETE FROM roster WHERE accountId=:account")
    protected abstract void clear(final long account);

    @Query("UPDATE account SET rosterVersion=:version WHERE id=:account")
    protected abstract void setRosterVersion(final long account, final String version);

    @Transaction
    public void setRoster(
            final Account account,
            final String version,
            final Collection<RosterItemEntity> rosterItems) {
        clear(account.id);
        insert(rosterItems);
        setRosterVersion(account.id, version);
    }
}
