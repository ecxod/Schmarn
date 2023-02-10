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
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.database.model.MessageIdentifier;
import im.conversations.android.database.model.Modification;
import im.conversations.android.transformer.Transformation;
import java.util.Collection;
import java.util.List;

@Dao
public abstract class MessageDao {

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

    // this gets either a message or a stub.
    // stubs are recognized by latestVersion=NULL
    // when found by stanzaId the stanzaId must either by verified or belonging to a stub
    // when found by messageId the from must either match (for corrections) or not be set (null) and
    // we only look up stubs
    // TODO the from matcher should be in the outer condition
    @Query(
            "SELECT id,stanzaId,messageId,fromBare,latestVersion FROM message WHERE chatId=:chatId"
                    + " AND (fromBare=:fromBare OR fromBare=NULL) AND ((stanzaId != NULL AND"
                    + " stanzaId=:stanzaId AND (stanzaIdVerified=1 OR latestVersion=NULL)) OR"
                    + " (stanzaId = NULL AND messageId=:messageId AND latestVersion = NULL))")
    abstract MessageIdentifier get(long chatId, Jid fromBare, String stanzaId, String messageId);

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
}
