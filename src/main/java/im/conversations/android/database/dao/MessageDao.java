package im.conversations.android.database.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessageStateEntity;
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.database.model.MessageIdentifier;
import im.conversations.android.database.model.MessageState;
import im.conversations.android.database.model.Modification;
import im.conversations.android.transformer.Transformation;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dao
public abstract class MessageDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDao.class);

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND toBare=:toBare AND"
                + " toResource=NULL AND chatId IN (SELECT id FROM chat WHERE accountId=:account)")
    abstract int acknowledge(long account, String messageId, final String toBare);

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND toBare=:toBare AND"
                    + " toResource=:toResource AND chatId IN (SELECT id FROM chat WHERE"
                    + " accountId=:account)")
    abstract int acknowledge(
            long account, final String messageId, final String toBare, final String toResource);

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

    // this method returns a MessageIdentifier (message + version) used to create ORIGINAL messages
    // it might return something that was previously a stub (message that only has reactions or
    // corrections but no original content). but in the process of invoking this method the stub
    // will be upgraded to an original message (missing information filled in)
    @Transaction
    public MessageIdentifier getOrCreateMessage(
            ChatIdentifier chatIdentifier, final Transformation transformation) {
        final MessageIdentifier messageIdentifier =
                get(
                        chatIdentifier.id,
                        transformation.fromBare(),
                        transformation.stanzaId,
                        transformation.messageId);
        if (messageIdentifier != null) {
            if (messageIdentifier.isStub()) {
                // TODO create version
                // TODO fill up information
                return messageIdentifier;
            } else {
                throw new IllegalStateException(
                        String.format(
                                "A message with stanzaId '%s' and messageId '%s' from %s already"
                                        + " exists",
                                transformation.stanzaId,
                                transformation.messageId,
                                transformation.from));
            }
        }
        final MessageEntity entity = MessageEntity.of(chatIdentifier.id, transformation);
        final long messageEntityId = insert(entity);
        final long messageVersionId =
                insert(
                        MessageVersionEntity.of(
                                messageEntityId, Modification.ORIGINAl, transformation));
        setLatestMessageId(messageEntityId, messageVersionId);
        return new MessageIdentifier(
                messageEntityId,
                transformation.stanzaId,
                transformation.messageId,
                transformation.fromBare(),
                messageVersionId);
    }

    // this gets either a message or a stub.
    // stubs are recognized by latestVersion=NULL
    // when found by stanzaId the stanzaId must either by verified or belonging to a stub
    // when found by messageId the from must either match (for corrections) or not be set (null) and
    // we only look up stubs
    // TODO the from matcher should be in the outer condition
    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                + " chatId=:chatId AND (fromBare=:fromBare OR fromBare=NULL) AND ((stanzaId !="
                + " NULL AND stanzaId=:stanzaId AND (stanzaIdVerified=1 OR latestVersion=NULL)) OR"
                + " (stanzaId = NULL AND messageId=:messageId AND latestVersion = NULL))")
    abstract MessageIdentifier get(long chatId, Jid fromBare, String stanzaId, String messageId);

    public MessageIdentifier getOrCreateVersion(
            ChatIdentifier chat,
            Transformation transformation,
            final String messageId,
            final Modification modification) {
        Preconditions.checkArgument(
                messageId != null, "A modification must reference a message id");
        final MessageIdentifier messageIdentifier;
        if (transformation.occupantId == null) {
            messageIdentifier = getByMessageId(chat.id, transformation.fromBare(), messageId);
        } else {
            messageIdentifier =
                    getByOccupantIdAndMessageId(
                            chat.id,
                            transformation.fromBare(),
                            transformation.occupantId,
                            messageId);
        }
        if (messageIdentifier == null) {
            LOGGER.info(
                    "Create stub for {} because we could not find anything with id {} from {}",
                    modification,
                    messageId,
                    transformation.fromBare());
            final var messageEntity = MessageEntity.stub(chat.id, messageId, transformation);
            final long messageEntityId = insert(messageEntity);
            final long messageVersionId =
                    insert(MessageVersionEntity.of(messageEntityId, modification, transformation));
            // we do not point latestVersion to this newly created versions. We've only created a
            // stub and are waiting for the original message to arrive
            return new MessageIdentifier(
                    messageEntityId, null, null, transformation.fromBare(), messageVersionId);
        }
        if (hasVersionWithMessageId(messageIdentifier.id, transformation.messageId)) {
            throw new IllegalStateException(
                    String.format(
                            "A modification with messageId %s has already been applied",
                            messageId));
        }
        final long messageVersionId =
                insert(MessageVersionEntity.of(messageIdentifier.id, modification, transformation));
        if (messageIdentifier.version != null) {
            // if the existing message was not a stub we retarget the version
            final long latestVersion = getLatestVersion(messageIdentifier.id);
            setLatestMessageId(messageIdentifier.id, latestVersion);
        }
        return new MessageIdentifier(
                messageIdentifier.id,
                messageIdentifier.stanzaId,
                messageIdentifier.messageId,
                messageIdentifier.fromBare,
                messageVersionId);
    }

    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                    + " chatId=:chatId AND (fromBare=:fromBare OR fromBare IS NULL) AND"
                    + " (occupantId=:occupantId OR occupantId IS NULL) AND messageId=:messageId")
    abstract MessageIdentifier getByOccupantIdAndMessageId(
            long chatId, Jid fromBare, String occupantId, String messageId);

    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                    + " chatId=:chatId AND (fromBare=:fromBare OR fromBare IS NULL) AND"
                    + " messageId=:messageId")
    abstract MessageIdentifier getByMessageId(long chatId, Jid fromBare, String messageId);

    @Query(
            "SELECT id FROM message_version WHERE messageEntityId=:messageEntityId ORDER BY (CASE"
                    + " modification WHEN 'ORIGINAL' THEN 0 ELSE 1 END),receivedAt DESC LIMIT 1")
    abstract Long getLatestVersion(long messageEntityId);

    @Query(
            "SELECT EXISTS (SELECT id FROM message_version WHERE messageEntityId=:messageEntityId"
                    + " AND messageId=:messageId)")
    abstract boolean hasVersionWithMessageId(long messageEntityId, String messageId);

    @Insert
    protected abstract long insert(MessageEntity messageEntity);

    @Insert
    protected abstract long insert(MessageVersionEntity messageVersionEntity);

    @Query("UPDATE message SET latestVersion=:messageVersionId WHERE id=:messageEntityId")
    protected abstract void setLatestMessageId(
            final long messageEntityId, final long messageVersionId);

    public Long getOrCreateStub(final Transformation transformation) {
        // TODO look up where parentId matches messageId (or stanzaId for group chats)

        // when creating stub either set from (correction) or don’t (other attachment)

        return null;
    }

    public void insertMessageContent(Long latestVersion, List<MessageContent> contents) {
        Preconditions.checkNotNull(
                latestVersion, "Contents can only be inserted for a specific version");
        Preconditions.checkArgument(
                contents.size() > 0,
                "If you are trying to insert empty contents something went wrong");
        insertMessageContent(
                Lists.transform(contents, c -> MessageContentEntity.of(latestVersion, c)));
    }

    @Insert
    protected abstract void insertMessageContent(Collection<MessageContentEntity> contentEntities);

    public void insertMessageState(
            ChatIdentifier chatIdentifier,
            final String messageId,
            final MessageState messageState) {
        final Long versionId = getVersionIdForOutgoingMessage(chatIdentifier.id, messageId);
        if (versionId == null) {
            LOGGER.warn(
                    "Can not find message {} in chat {} ({})",
                    messageId,
                    chatIdentifier.id,
                    chatIdentifier.address);
            return;
        }
        insert(MessageStateEntity.of(versionId, messageState));
    }

    @Query(
            "SELECT message_version.id FROM message_version JOIN message ON"
                    + " message.id=message_version.messageEntityId WHERE message.chatId=:chatId AND"
                    + " message_version.messageId=:messageId AND message.outgoing=1")
    protected abstract Long getVersionIdForOutgoingMessage(long chatId, final String messageId);

    @Insert
    protected abstract void insert(MessageStateEntity messageStateEntity);
}
