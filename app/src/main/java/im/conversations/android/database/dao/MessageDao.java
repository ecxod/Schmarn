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
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessageReactionEntity;
import im.conversations.android.database.entity.MessageStateEntity;
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.Encryption;
import im.conversations.android.database.model.MessageIdentifier;
import im.conversations.android.database.model.MessageState;
import im.conversations.android.database.model.MessageWithContentReactions;
import im.conversations.android.database.model.Modification;
import im.conversations.android.transformer.MessageContentWrapper;
import im.conversations.android.transformer.MessageTransformation;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Collection;
import java.util.List;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;

@Dao
public abstract class MessageDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDao.class);

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND toBare=:toBare AND"
                + " toResource=NULL AND chatId IN (SELECT id FROM chat WHERE accountId=:account)")
    abstract int acknowledge(long account, String messageId, final BareJid toBare);

    @Query(
            "UPDATE message SET acknowledged=1 WHERE messageId=:messageId AND toBare=:toBare AND"
                    + " toResource=:toResource AND chatId IN (SELECT id FROM chat WHERE"
                    + " accountId=:account)")
    abstract int acknowledge(
            long account,
            final String messageId,
            final BareJid toBare,
            final Resourcepart toResource);

    public boolean acknowledge(
            final Account account, @NonNull final String messageId, @NonNull final Jid to) {
        return acknowledge(account.id, messageId, to);
    }

    public boolean acknowledge(
            final long account, @NonNull final String messageId, @NonNull final Jid to) {
        if (to.hasResource()) {
            return acknowledge(account, messageId, to.asBareJid(), to.getResourceOrThrow()) > 0;
        } else {
            return acknowledge(account, messageId, to.asBareJid()) > 0;
        }
    }

    // this method returns a MessageIdentifier (message + version) used to create ORIGINAL messages
    // it might return something that was previously a stub (message that only has reactions or
    // corrections but no original content). but in the process of invoking this method the stub
    // will be upgraded to an original message (missing information filled in)
    @Transaction
    public MessageIdentifier getOrCreateMessage(
            ChatIdentifier chatIdentifier, final MessageTransformation transformation) {
        final MessageIdentifier messageIdentifier =
                get(
                        chatIdentifier.id,
                        transformation.fromBare(),
                        transformation.occupantId,
                        transformation.stanzaId,
                        transformation.messageId);
        if (messageIdentifier != null) {
            if (messageIdentifier.isStub()) {
                if (transformation.type == Message.Type.GROUPCHAT) {
                    mergeMessageStubs(chatIdentifier, messageIdentifier, transformation);
                }
                LOGGER.info(
                        "Found stub for stanzaId '{}' and messageId '{}'",
                        transformation.stanzaId,
                        transformation.messageId);
                final long originalVersionId =
                        insert(
                                MessageVersionEntity.of(
                                        messageIdentifier.id,
                                        Modification.ORIGINAL,
                                        transformation));
                final MessageEntity updatedEntity =
                        MessageEntity.of(chatIdentifier.id, transformation);
                updatedEntity.id = messageIdentifier.id;
                updatedEntity.latestVersion = getLatestVersion(messageIdentifier.id);
                LOGGER.info(
                        "Created original version {} and updated latest version to {} for"
                                + " messageEntityId {}",
                        originalVersionId,
                        updatedEntity.latestVersion,
                        messageIdentifier.id);
                update(updatedEntity);
                return new MessageIdentifier(
                        updatedEntity.id,
                        transformation.stanzaId,
                        transformation.messageId,
                        transformation.fromBare(),
                        originalVersionId);
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
                                messageEntityId, Modification.ORIGINAL, transformation));
        setLatestMessageId(messageEntityId, messageVersionId);
        return new MessageIdentifier(
                messageEntityId,
                transformation.stanzaId,
                transformation.messageId,
                transformation.fromBare(),
                messageVersionId);
    }

    private void mergeMessageStubs(
            ChatIdentifier chatIdentifier,
            MessageIdentifier messageIdentifier,
            final MessageTransformation transformation) {
        final Long stub;
        if (messageIdentifier.messageId == null && transformation.messageId != null) {
            stub = getMessageStubByMessageId(chatIdentifier.id, transformation.messageId);
        } else if (messageIdentifier.stanzaId == null && transformation.stanzaId != null) {
            stub = getMessageStubByStanzaId(chatIdentifier.id, transformation.stanzaId);
        } else {
            return;
        }
        if (stub == null) {
            return;
        }
        LOGGER.info("Updating message.id in dangling stub {} => {}", stub, messageIdentifier.id);
        updateMessageEntityIdInReactions(stub, messageIdentifier.id);
        updateMessageEntityIdInVersions(stub, messageIdentifier.id);
        deleteMessageEntity(stub);
    }

    @Query(
            "UPDATE message_reaction SET messageEntityId=:newMessageEntityId WHERE"
                    + " messageEntityId=:oldMessageEntityId")
    protected abstract void updateMessageEntityIdInReactions(
            long oldMessageEntityId, Long newMessageEntityId);

    @Query(
            "UPDATE message_version SET messageEntityId=:newMessageEntityId WHERE"
                    + " messageEntityId=:oldMessageEntityId")
    protected abstract void updateMessageEntityIdInVersions(
            long oldMessageEntityId, Long newMessageEntityId);

    @Query("DELETE FROM message WHERE id=:messageEntityId")
    protected abstract void deleteMessageEntity(final long messageEntityId);

    @Query(
            "SELECT id FROM message WHERE chatId=:chatId AND messageId=:messageId AND stanzaId IS"
                    + " NULL AND latestVersion IS NULL")
    protected abstract Long getMessageStubByMessageId(long chatId, String messageId);

    @Query(
            "SELECT id FROM message WHERE chatId=:chatId AND stanzaId=:stanzaId AND messageId IS"
                    + " NULL AND latestVersion IS NULL")
    protected abstract Long getMessageStubByStanzaId(long chatId, String stanzaId);

    // this gets either a message or a stub.
    // stubs are recognized by latestVersion=NULL
    // when found by stanzaId the stanzaId must either by verified or belonging to a stub
    // when found by messageId the from must either match (for corrections) or not be set (null) and
    // we only look up stubs
    // TODO `senderIdentity` should probably match too
    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                + " chatId=:chatId AND (fromBare=:fromBare OR fromBare IS NULL) AND"
                + " (occupantId=:occupantId OR occupantId IS NULL) AND ((stanzaId IS NOT NULL AND"
                + " stanzaId=:stanzaId AND (stanzaIdVerified=1 OR latestVersion IS NULL)) OR"
                + " (stanzaId IS NULL AND messageId=:messageId AND latestVersion IS NULL))")
    abstract MessageIdentifier get(
            long chatId, BareJid fromBare, String occupantId, String stanzaId, String messageId);

    public MessageIdentifier getOrCreateVersion(
            ChatIdentifier chat,
            MessageTransformation transformation,
            final String messageId,
            final Modification modification) {
        Preconditions.checkArgument(
                messageId != null, "A modification must reference a message id");
        final MessageIdentifier messageIdentifier;
        if (transformation.type == Message.Type.GROUPCHAT) {
            // TODO if modification == moderation do not take occupant Id into account
            Preconditions.checkNotNull(
                    transformation.occupantId,
                    "To create a version of a group chat message occupant id must be set");
            messageIdentifier =
                    getByOccupantIdAndMessageId(
                            chat.id,
                            transformation.fromBare(),
                            transformation.occupantId,
                            messageId);
        } else {
            messageIdentifier = getByMessageId(chat.id, transformation.fromBare(), messageId);
        }
        if (messageIdentifier == null) {
            LOGGER.info(
                    "Create stub for {} because we could not find anything with id {} from {}",
                    modification,
                    messageId,
                    transformation.fromBare());
            // TODO when creating a stub for 'moderation' we should not include occupant id and
            // senderId in there since we don’t know who those are
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
            long chatId, BareJid fromBare, String occupantId, String messageId);

    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion as version FROM message WHERE"
                    + " chatId=:chatId AND (fromBare=:fromBare OR fromBare IS NULL) AND"
                    + " messageId=:messageId")
    abstract MessageIdentifier getByMessageId(long chatId, BareJid fromBare, String messageId);

    @Query(
            "SELECT id FROM message_version WHERE messageEntityId=:messageEntityId ORDER BY (CASE"
                    + " modification WHEN 'ORIGINAL' THEN 1 ELSE 0 END),receivedAt DESC LIMIT 1")
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

    public void insertMessageContent(
            final Long latestVersion, final MessageContentWrapper messageContentWrapper) {
        Preconditions.checkNotNull(
                latestVersion, "Contents can only be inserted for a specific version");
        Preconditions.checkArgument(
                messageContentWrapper.contents.size() > 0,
                "If you are trying to insert empty contents something went wrong");
        insertMessageContent(
                Lists.transform(
                        messageContentWrapper.contents,
                        c -> MessageContentEntity.of(latestVersion, c)));
        final int rows =
                updateMessageVersionEncryption(
                        latestVersion,
                        messageContentWrapper.encryption,
                        messageContentWrapper.identityKey);
        if (rows != 1) {
            throw new IllegalStateException(
                    "We expected to update encryption information on exactly 1 row");
        }
    }

    @Insert
    protected abstract void insertMessageContent(Collection<MessageContentEntity> contentEntities);

    @Query(
            "UPDATE message_version SET encryption=:encryption,identityKey=:identityKey WHERE"
                    + " id=:messageVersionId")
    protected abstract int updateMessageVersionEncryption(
            long messageVersionId, Encryption encryption, IdentityKey identityKey);

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
            ChatIdentifier chat, Reactions reactions, MessageTransformation transformation) {
        final Message.Type messageType = transformation.type;
        final MessageIdentifier messageIdentifier =
                getOrCreateStub(chat, messageType, reactions.getId());
        if (messageType == Message.Type.GROUPCHAT) {
            Preconditions.checkNotNull(
                    transformation.occupantId,
                    "OccupantId must not be null when processing reactions in group chats");
            deleteReactionsByOccupantId(messageIdentifier.id, transformation.occupantId);
        } else {
            deleteReactionsByFromBare(messageIdentifier.id, transformation.fromBare());
        }
        LOGGER.info(
                "Inserting reaction from {} to messageEntityId {}",
                transformation.from,
                messageIdentifier.id);
        insertReactions(
                Collections2.transform(
                        reactions.getReactions(),
                        r -> MessageReactionEntity.of(messageIdentifier.id, r, transformation)));
    }

    @Query(
            "DELETE FROM message_reaction WHERE messageEntityId=:messageEntityId AND"
                    + " occupantId=:occupantId")
    protected abstract void deleteReactionsByOccupantId(long messageEntityId, String occupantId);

    @Query(
            "DELETE FROM message_reaction WHERE messageEntityId=:messageEntityId AND"
                    + " reactionBy=:fromBare")
    protected abstract void deleteReactionsByFromBare(long messageEntityId, BareJid fromBare);

    @Transaction
    @Query(
            "SELECT message.id as"
                + " id,sentAt,outgoing,toBare,toResource,fromBare,fromResource,modification,latestVersion"
                + " as version,inReplyToMessageEntityId,encryption,message_version.identityKey,trust"
                + " FROM chat JOIN message on message.chatId=chat.id JOIN message_version ON"
                + " message.latestVersion=message_version.id LEFT JOIN axolotl_identity ON"
                + " chat.accountId=axolotl_identity.accountId AND"
                + " message.senderIdentity=axolotl_identity.address AND"
                + " message_version.identityKey=axolotl_identity.identityKey WHERE chat.id=:chatId"
                + " AND latestVersion IS NOT NULL ORDER BY message.receivedAt")
    public abstract List<MessageWithContentReactions> getMessages(long chatId);

    public void setInReplyTo(
            ChatIdentifier chat,
            MessageIdentifier messageIdentifier,
            Message.Type messageType,
            final Jid to,
            String inReplyTo) {
        if (messageType == Message.Type.GROUPCHAT) {
            final Long messageEntityId = getMessageByStanzaId(chat.id, inReplyTo);
            setInReplyToStanzaId(messageIdentifier.id, inReplyTo, messageEntityId);
        } else {
            final Long messageEntityId = getMessageByMessageId(chat.id, to.asBareJid(), inReplyTo);
            setInReplyToMessageId(messageIdentifier.id, inReplyTo, messageEntityId);
        }
    }

    @Query(
            "UPDATE message SET"
                + " inReplyToMessageId=null,inReplyToStanzaId=:stanzaId,inReplyToMessageEntityId=:inReplyToMessageEntityId"
                + " WHERE id=:id")
    protected abstract void setInReplyToStanzaId(
            final long id, String stanzaId, long inReplyToMessageEntityId);

    @Query(
            "UPDATE message SET"
                + " inReplyToMessageId=:messageId,inReplyToStanzaId=null,inReplyToMessageEntityId=:inReplyToMessageEntityId"
                + " WHERE id=:id")
    protected abstract void setInReplyToMessageId(
            final long id, String messageId, long inReplyToMessageEntityId);

    @Query(
            "SELECT id FROM message WHERE chatId=:chatId AND fromBare=:fromBare AND"
                    + " messageId=:messageId")
    protected abstract Long getMessageByMessageId(long chatId, BareJid fromBare, String messageId);

    @Query("SELECT id FROM message WHERE chatId=:chatId AND stanzaId=:stanzaId")
    protected abstract Long getMessageByStanzaId(long chatId, String stanzaId);
}
