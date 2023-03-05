package im.conversations.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import im.conversations.android.database.entity.ChatEntity;
import im.conversations.android.database.entity.MucStatusCodeEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.ChatType;
import im.conversations.android.database.model.GroupIdentifier;
import im.conversations.android.database.model.MucState;
import im.conversations.android.database.model.MucWithNick;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dao
public abstract class ChatDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatDao.class);

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

    @Query(
            "SELECT id,address,type,archived FROM chat WHERE accountId=:accountId AND"
                    + " address=:address AND type=:chatType")
    public abstract ChatIdentifier get(
            final long accountId, final Jid address, final ChatType chatType);

    @Insert
    protected abstract long insert(ChatEntity chatEntity);

    @Query("UPDATE chat SET archived=:archived WHERE chat.id=:chatId")
    public abstract void setArchived(final long chatId, final boolean archived);

    @Query(
            "UPDATE chat SET archived=:archived WHERE chat.accountId=:account AND"
                    + " chat.address=:address")
    protected abstract void setArchived(
            final long account, final String address, final boolean archived);

    @Query("SELECT id,name FROM `group` ORDER BY name")
    public abstract LiveData<List<GroupIdentifier>> getGroups();

    @Transaction
    public void syncWithBookmarks(final Account account) {
        final var chatsNotInBookmarks = getChatsNotInBookmarks(account.id, ChatType.MUC);
        final var bookmarksNotInChat = getBookmarksNotInChats(account.id, ChatType.MUC);
        LOGGER.info("chatsNotInBookmark {}", chatsNotInBookmarks);
        LOGGER.info("bookmarkNotInChat {}", bookmarksNotInChat);
        archive(account.id, chatsNotInBookmarks);
        createOrUnarchiveMuc(account.id, bookmarksNotInChat);
    }

    private void archive(final long account, final List<String> addresses) {
        for (final String address : addresses) {
            setArchived(account, address, true);
        }
    }

    private void createOrUnarchiveMuc(final long account, final List<Jid> addresses) {
        for (final Jid address : addresses) {
            createOrUnarchiveMuc(account, address);
        }
    }

    private void createOrUnarchiveMuc(final long account, final Jid address) {
        final var bareJid = address.asBareJid();
        final var existing = get(account, bareJid);
        if (existing != null) {
            if (existing.archived) {
                setArchived(existing.id, false);
            }
            return;
        }
        final var entity = new ChatEntity();
        entity.accountId = account;
        entity.address = bareJid.toString();
        entity.type = ChatType.MUC;
        entity.archived = false;
        insert(entity);
    }

    @Query(
            "SELECT chat.address FROM chat WHERE chat.accountId=:account AND chat.type=:chatType"
                    + " AND archived=0 EXCEPT SELECT bookmark.address FROM bookmark WHERE"
                    + " bookmark.accountId=:account AND bookmark.autoJoin=1")
    protected abstract List<String> getChatsNotInBookmarks(long account, ChatType chatType);

    @Query(
            "SELECT bookmark.address FROM bookmark WHERE bookmark.accountId=accountId AND"
                    + " bookmark.autoJoin=1 EXCEPT SELECT chat.address FROM chat WHERE"
                    + " chat.accountId=:account AND chat.type=:chatType AND archived=0")
    protected abstract List<Jid> getBookmarksNotInChats(long account, ChatType chatType);

    @Query(
            "SELECT chat.id as chatId,chat.address,bookmark.nick as nickBookmark,nick.nick as"
                + " nickAccount FROM chat LEFT JOIN bookmark ON chat.accountId=bookmark.accountId"
                + " AND chat.address=bookmark.address JOIN account ON account.id=chat.accountId"
                + " LEFT JOIN nick ON nick.accountId=chat.accountId AND"
                + " nick.address=account.address WHERE chat.accountId=:account AND"
                + " chat.type=:chatType AND chat.archived=0 AND chat.mucState IS NULL")
    public abstract ListenableFuture<List<MucWithNick>> getMultiUserChats(
            final long account, final ChatType chatType);

    @Query("UPDATE chat SET mucState=:mucState, errorCondition=:errorCondition WHERE id=:chatId")
    protected abstract void setMucStateInternal(
            final long chatId, final MucState mucState, final String errorCondition);

    @Transaction
    public void setMucState(
            final long chatId, final MucState mucState, final String errorCondition) {
        setMucStateInternal(chatId, mucState, errorCondition);
        deleteStatusCodes(chatId);
    }

    @Transaction
    public void setMucState(
            final long chatId, final MucState mucState, final Collection<Integer> statusCodes) {
        setMucStateInternal(chatId, mucState, null);
        deleteStatusCodes(chatId);
        insertStatusCode(MucStatusCodeEntity.of(chatId, statusCodes));
    }

    @Transaction
    public void setMucState(final long chatId, final MucState mucState) {
        setMucStateInternal(chatId, mucState, null);
        deleteStatusCodes(chatId);
    }

    @Transaction
    public void resetMucStates() {
        this.nullMucStates();
        this.deleteStatusCodes();
    }

    @Insert
    protected abstract void insertStatusCode(final Collection<MucStatusCodeEntity> entities);

    @Query("UPDATE chat SET mucState=null,errorCondition=null")
    protected abstract void nullMucStates();

    @Query("DELETE FROM muc_status_code")
    protected abstract void deleteStatusCodes();

    @Query("DELETE FROM muc_status_code WHERE chatId=:chatId")
    protected abstract void deleteStatusCodes(final long chatId);
}
