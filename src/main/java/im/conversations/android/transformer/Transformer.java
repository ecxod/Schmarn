package im.conversations.android.transformer;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.xmpp.model.DeliveryReceipt;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.muc.user.MultiUserChat;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transformer.class);

    private final Context context;
    private final Account account;

    public Transformer(final Context context, final Account account) {
        this.context = context;
        this.account = account;
    }

    public boolean transform(final Transformation transformation) {
        final var database = ConversationsDatabase.getInstance(context);
        return database.runInTransaction(() -> transform(database, transformation));
    }

    /**
     * @param transformation
     * @return returns true if there is something we want to send a delivery receipt for. Basically
     *     anything that created a new message in the database. Notably not something that only
     *     updated a status somewhere
     */
    private boolean transform(
            final ConversationsDatabase database, final Transformation transformation) {
        final var remote = transformation.remote;
        final var messageType = transformation.type;
        final var deliveryReceipt = transformation.getExtension(DeliveryReceipt.class);
        final Replace lastMessageCorrection = transformation.getExtension(Replace.class);
        final var muc = transformation.getExtension(MultiUserChat.class);


        final List<MessageContent> contents = parseContent(transformation);

        // TODO this also needs to be true for retractions once we support those (anything that
        // creates a new message version
        final boolean versionModification = Objects.nonNull(lastMessageCorrection);

        // TODO get or create Cha

        final ChatIdentifier chat =
                database.chatDao()
                        .getOrCreateChat(account, remote, messageType, Objects.nonNull(muc));

        if (contents.isEmpty()) {
            LOGGER.info("Received message from {} w/o contents", transformation.from);
            // TODO apply errors, displayed, received etc
            // TODO apply reactions
        } else {
            if (versionModification) {
                // TODO use getOrStub
                // TODO check if versionModification has already been applied

                // TODO for replaced message create a new version; re-target latestVersion

            } else {
                final var messageIdentifier =
                        database.messageDao().getOrCreateMessage(chat, transformation);
                database.messageDao()
                        .insertMessageContent(messageIdentifier.latestVersion, contents);
                return true;
            }
        }
        return true;
    }

    protected List<MessageContent> parseContent(final Transformation transformation) {
        final var encrypted = transformation.getExtension(Encrypted.class);
        final var encryptedWithPayload = encrypted != null && encrypted.hasPayload();
        final Collection<Body> bodies = transformation.getExtensions(Body.class);
        final Collection<OutOfBandData> outOfBandData =
                transformation.getExtensions(OutOfBandData.class);
        final ImmutableList.Builder<MessageContent> messageContentBuilder = ImmutableList.builder();

        // TODO decrypt

        if (bodies.size() == 1 && outOfBandData.size() == 1) {
            final String text = Iterables.getOnlyElement(bodies).getContent();
            final String url = Iterables.getOnlyElement(outOfBandData).getURL();
            if (!Strings.isNullOrEmpty(url) && url.equals(text)) {
                return ImmutableList.of(MessageContent.file(url));
            }
        }

        // TODO verify that body is not fallback
        for (final Body body : bodies) {
            final String text = body.getContent();
            if (Strings.isNullOrEmpty(text)) {
                continue;
            }
            messageContentBuilder.add(MessageContent.text(text, body.getLang()));
        }
        for (final OutOfBandData data : outOfBandData) {
            final String url = data.getURL();
            if (Strings.isNullOrEmpty(url)) {
                continue;
            }
            messageContentBuilder.add(MessageContent.file(url));
        }
        return messageContentBuilder.build();
    }
}
