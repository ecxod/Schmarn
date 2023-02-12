package im.conversations.android.database.dao;

import androidx.annotation.NonNull;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessageReactionEntity;
import im.conversations.android.database.entity.MessageStateEntity;
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.database.model.MessageIdentifier;
import im.conversations.android.database.model.MessageState;
import im.conversations.android.database.model.MessageWithContentReactions;
import im.conversations.android.database.model.Modification;
import im.conversations.android.transformer.Transformation;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.stanza.Message;
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
                LOGGER.info(
                        "Found stub for stanzaId '{}' and messageId '{}'",
                        transformation.stanzaId,
                        transformation.messageId);
                final long messageVersionId =
                        insert(
                                MessageVersionEntity.of(
                                        messageIdentifier.id,
                                        Modification.ORIGINAl,
                                        transformation));
                final MessageEntity updatedEntity =
                        MessageEntity.of(chatIdentifier.id, transformation);
                updatedEntity.id = messageIdentifier.id;
                updatedEntity.latestVersion = messageVersionId;
                update(updatedEntity);
                return new MessageIdentifier(
                        updatedEntity.id,
                        transformation.stanzaId,
                        transformation.messageId,
                        transformation.fromBare(),
                        messageVersionId);
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
    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                + " chatId=:chatId AND (fromBare=:fromBare OR fromBare IS NULL) AND ((stanzaId IS"
                + " NOT NULL AND stanzaId=:stanzaId AND (stanzaIdVerified=1 OR latestVersion IS"
                + " NULL)) OR (stanzaId IS NULL AND messageId=:messageId AND latestVersion IS"
                + " NULL))")
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

    @Update
    protected abstract void update(final MessageEntity messageEntity);

    @Insert
    protected abstract long insert(MessageVersionEntity messageVersionEntity);

    @Query("UPDATE message SET latestVersion=:messageVersionId WHERE id=:messageEntityId")
    protected abstract void setLatestMessageId(
            final long messageEntityId, final long messageVersionId);

    public MessageIdentifier getOrCreateStub(
            final ChatIdentifier chat, final Message.Type messageType, final String parentId) {
        final MessageIdentifier existing;
        if (messageType == Message.Type.GROUPCHAT) {
            existing = getByStanzaId(chat.id, parentId);
        } else {
            existing = getByMessageId(chat.id, parentId);
        }
        if (existing != null) {
            return existing;
        }
        final MessageEntity messageEntity;
        if (messageType == Message.Type.GROUPCHAT) {
            LOGGER.info("Create stub for stanza id {}", parentId);
            messageEntity = MessageEntity.stubOfStanzaId(chat.id, parentId);
        } else {
            LOGGER.info("Create stub for message id {}", parentId);
            messageEntity = MessageEntity.stubOfMessageId(chat.id, parentId);
        }
        final long messageEntityId = insert(messageEntity);
        return new MessageIdentifier(messageEntityId, null, null, null, null);
    }

    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                    + " chatId=:chatId AND messageId=:messageId")
    protected abstract MessageIdentifier getByMessageId(final long chatId, final String messageId);

    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                    + " chatId=:chatId AND stanzaId=:stanzaId")
    protected abstract MessageIdentifier getByStanzaId(final long chatId, final String stanzaId);

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

    @Insert
    protected abstract void insertReactions(Collection<MessageReactionEntity> reactionEntities);

    public void insertReactions(
            ChatIdentifier chat, Reactions reactions, Transformation transformation) {
        final Message.Type messageType = transformation.type;
        final MessageIdentifier messageIdentifier =
                getOrCreateStub(chat, messageType, reactions.getId());
        // TODO delete old reactions
        insertReactions(
                Collections2.transform(
                        reactions.getReactions(),
                        r -> MessageReactionEntity.of(messageIdentifier.id, r, transformation)));
    }

    @Transaction
    @Query(
            "SELECT message.id as"
                + " id,sentAt,outgoing,toBare,toResource,fromBare,fromResource,modification,latestVersion"
                + " as version FROM message JOIN message_version ON"
                + " message.latestVersion=message_version.id WHERE message.chatId=:chatId AND"
                + " latestVersion IS NOT NULL ORDER BY message.receivedAt")
    public abstract List<MessageWithContentReactions> getMessages(long chatId);
}
