package im.conversations.android.database.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Query;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.model.Account;

@Dao
public abstract class MessageDao {

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND bareTo=:bareTo AND"
                + " toResource=NULL AND chatId IN (SELECT id FROM chat WHERE accountId=:account)")
    abstract int acknowledge(long account, String messageId, final String bareTo);

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND bareTo=:bareTo AND"
                    + " toResource=:toResource AND chatId IN (SELECT id FROM chat WHERE"
                    + " accountId=:account)")
    abstract int acknowledge(
            long account, final String messageId, final String bareTo, final String toResource);

    public boolean acknowledge(
            final Account account, @NonNull final String messageId, @NonNull final Jid to) {
        return acknowledge(account.id, messageId, to);
    }

    public boolean acknowledge(
            final long account, @NonNull final String messageId, @NonNull final Jid to) {
        if (to.isBareJid()) {
            return acknowledge(account, messageId, to.toEscapedString()) > 0;
        } else {
            return acknowledge(
                            account, messageId, to.asBareJid().toEscapedString(), to.getResource())
                    > 0;
        }
    }
}
