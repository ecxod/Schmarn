package im.conversations.android.database.dao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import im.conversations.android.database.entity.PresenceEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.PresenceShow;
import im.conversations.android.database.model.PresenceType;
import im.conversations.android.xmpp.model.muc.user.MultiUserChat;
import java.util.Arrays;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.parts.Resourcepart;

@Dao
public abstract class PresenceDao {

    @Query("DELETE FROM presence WHERE accountId=:account")
    public abstract void deletePresences(long account);

    @Query("DELETE FROM presence WHERE accountId=:account AND address=:address")
    abstract void deletePresences(long account, BareJid address);

    @Query(
            "DELETE FROM presence WHERE accountId=:account AND address=:address AND"
                    + " resource=:resource")
    abstract void deletePresence(long account, BareJid address, Resourcepart resource);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract void insert(PresenceEntity entity);

    public void set(
            @NonNull final Account account,
            @NonNull final BareJid address,
            @NonNull final Resourcepart resource,
            @Nullable final PresenceType type,
            @Nullable final PresenceShow show,
            @Nullable final String status,
            @Nullable final String vCardPhoto,
            @Nullable final String occupantId,
            @Nullable final MultiUserChat multiUserChat) {
        if (resource.equals(Resourcepart.EMPTY)
                && Arrays.asList(PresenceType.ERROR, PresenceType.UNAVAILABLE).contains(type)) {
            deletePresences(account.id, address);
        }
        if (type == PresenceType.UNAVAILABLE) {
            if (!resource.equals(Resourcepart.EMPTY)) {
                deletePresence(account.id, address, resource);
            }
            // unavailable presence only delete previous nothing left to do
            return;
        }
        final var entity =
                PresenceEntity.of(
                        account.id,
                        address,
                        resource,
                        type,
                        show,
                        status,
                        vCardPhoto,
                        occupantId,
                        multiUserChat);
        insert(entity);
    }
}
