package im.conversations.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import im.conversations.android.database.entity.ChatEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.ChatType;
import im.conversations.android.database.model.GroupIdentifier;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Arrays;
import java.util.List;
import org.jxmpp.jid.Jid;

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
        // TODO do not create entity for 'error'
        final var entity = new ChatEntity();
        entity.accountId = account.id;
        entity.address = address.toString();
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

    @Query("SELECT id,name FROM `group` ORDER BY name")
    public abstract LiveData<List<GroupIdentifier>> getGroups();
}
