package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.AvatarAdditionalEntity;
import im.conversations.android.database.entity.AvatarEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.avatar.Info;
import java.util.Collection;

@Dao
public abstract class AvatarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insert(AvatarEntity avatar);

    @Insert
    protected abstract void insert(Collection<AvatarAdditionalEntity> entities);

    public void set(
            final Account account,
            final Jid address,
            final Info thumbnail,
            final Collection<Info> additional) {
        final long id = insert(AvatarEntity.of(account, address, thumbnail));
        insert(Collections2.transform(additional, a -> AvatarAdditionalEntity.of(id, a)));
    }
}
