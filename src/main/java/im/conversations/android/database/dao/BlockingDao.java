package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.BlockedItemEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.blocking.Item;
import java.util.Collection;

@Dao
public abstract class BlockingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(Collection<BlockedItemEntity> entities);

    @Query("DELETE FROM blocked WHERE accountId=:account")
    public abstract void clear(final long account);

    @Transaction
    public void set(final Account account, final Collection<Item> blockedItems) {
        final var entities =
                Collections2.transform(
                        blockedItems, i -> BlockedItemEntity.of(account.id, i.getJid()));
        clear(account.id);
        insert(entities);
    }

    public void add(Account account, Collection<Jid> addresses) {
        insert(Collections2.transform(addresses, a -> BlockedItemEntity.of(account.id, a)));
    }

    @Query("DELETE from blocked WHERE accountId=:account AND address IN(:addresses)")
    public abstract void remove(final long account, Collection<Jid> addresses);
}
