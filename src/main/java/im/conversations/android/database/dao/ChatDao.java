package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.ChatEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.ChatType;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Arrays;

@Dao
public abstract class ChatDao {

    @Transaction
    public ChatIdentifier getOrCreateChat(
            final Account account,
            final Jid remote,
            final Message.Type messageType,
            final boolean multiUserChat) {
        final ChatType chatType;
        if (multiUserChat
                && Arrays.asList(Message.Type.CHAT, Message.Type.NORMAL).contains(messageType)) {
            chatType = ChatType.MUC_PM;
        } else if (messageType == Message.Type.GROUPCHAT) {
            chatType = ChatType.MUC;
        } else {
            chatType = ChatType.INDIVIDUAL;
        }
        final Jid address = chatType == ChatType.MUC_PM ? remote : remote.asBareJid();
        final ChatIdentifier existing = get(account.id, address);
        if (existing != null) {
            return existing;
        }
        final var entity = new ChatEntity();
        entity.accountId = account.id;
        entity.address = address.toEscapedString();
        entity.type = chatType;
        entity.archived = true;
        final long id = insert(entity);
        return new ChatIdentifier(id, address, chatType, true);
    }

    @Query(
            "SELECT id,address,type,archived FROM chat WHERE accountId=:accountId AND"
                    + " address=:address")
    protected abstract ChatIdentifier get(final long accountId, final Jid address);

    @Insert
    protected abstract long insert(ChatEntity chatEntity);
}
