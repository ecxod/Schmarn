package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.collect.Collections2;
import im.conversations.android.database.entity.BlockedItemEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.blocking.Item;
import java.util.Collection;

@Dao
public abstract class BlockingDao {

    @Insert
    abstract void insert(Collection<BlockedItemEntity> entities);

    @Query("DELETE FROM blocked WHERE accountId=:account")
    abstract void clear(final long account);

    @Transaction
    public void setBlocklist(final Account account, final Collection<Item> blockedItems) {
        final var entities =
                Collections2.transform(blockedItems, i -> BlockedItemEntity.of(account.id, i));
        clear(account.id);
        insert(entities);
    }
}
