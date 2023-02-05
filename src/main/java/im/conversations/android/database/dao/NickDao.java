package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.NickEntity;
import im.conversations.android.database.model.Account;

@Dao
public abstract class NickDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insert(NickEntity nickEntity);

    public long set(final Account account, final Jid address, final String nick) {
        return insert(NickEntity.of(account.id, address, nick));
    }
}
