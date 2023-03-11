package im.conversations.android.transformer;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import im.conversations.android.axolotl.AxolotlDecryptionException;
import im.conversations.android.axolotl.AxolotlPayload;
import im.conversations.android.axolotl.NotEncryptedForThisDeviceException;
import im.conversations.android.database.model.Encryption;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.database.model.PartType;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.whispersystems.libsignal.IdentityKey;

public class MessageContentWrapper {

    public static final MessageContentWrapper RETRACTION =
            new MessageContentWrapper(
                    ImmutableList.of(new MessageContent(null, PartType.RETRACTION, null, null)),
                    Encryption.CLEARTEXT,
                    null);

    private static final List<MessageContent> NOT_ENCRYPTED_FOR_THIS_DEVICE =
            ImmutableList.of(
                    new MessageContent(null, PartType.NOT_ENCRYPTED_FOR_THIS_DEVICE, null, null));

    public final List<MessageContent> contents;
    public final Encryption encryption;
    public final IdentityKey identityKey;

    private MessageContentWrapper(
            List<MessageContent> contents, Encryption encryption, IdentityKey identityKey) {
        if (encryption == Encryption.OMEMO) {
            Preconditions.checkArgument(
                    Objects.nonNull(identityKey),
                    "OMEMO encrypted content must provide an identity key");
        }
        this.contents = contents;
        this.encryption = encryption;
        this.identityKey = identityKey;
    }

    public static MessageContentWrapper parseCleartext(final MessageTransformation transformation) {
        final Collection<Body> bodies = transformation.getExtensions(Body.class);
        final Collection<OutOfBandData> outOfBandData =
                transformation.getExtensions(OutOfBandData.class);
        final ImmutableList.Builder<MessageContent> messageContentBuilder = ImmutableList.builder();

        if (bodies.size() == 1 && outOfBandData.size() == 1) {
            final String text = Iterables.getOnlyElement(bodies).getContent();
            final String url = Iterables.getOnlyElement(outOfBandData).getURL();
            if (!Strings.isNullOrEmpty(url) && url.equals(text)) {
                return cleartext(ImmutableList.of(MessageContent.file(url)));
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
        return cleartext(messageContentBuilder.build());
    }

    private static MessageContentWrapper cleartext(final List<MessageContent> contents) {
        return new MessageContentWrapper(contents, Encryption.CLEARTEXT, null);
    }

    public static MessageContentWrapper ofAxolotl(final AxolotlPayload payload) {
        if (payload.hasPayload()) {
            return new MessageContentWrapper(
                    ImmutableList.of(MessageContent.text(payload.payloadAsString(), null)),
                    Encryption.OMEMO,
                    payload.identityKey);
        }
        throw new IllegalArgumentException(
                String.format("%s does not have payload", payload.getClass().getSimpleName()));
    }

    public static MessageContentWrapper ofAxolotlException(final AxolotlDecryptionException e) {
        final Throwable cause = Throwables.getRootCause(e);
        if (cause instanceof NotEncryptedForThisDeviceException) {
            return new MessageContentWrapper(
                    NOT_ENCRYPTED_FOR_THIS_DEVICE, Encryption.FAILURE, null);
        } else {
            return new MessageContentWrapper(
                    ImmutableList.of(
                            new MessageContent(
                                    null,
                                    PartType.DECRYPTION_FAILURE,
                                    exceptionToMessage(cause),
                                    null)),
                    Encryption.FAILURE,
                    null);
        }
    }

    private static String exceptionToMessage(final Throwable throwable) {
        final String message = throwable.getMessage();
        return message == null ? throwable.getClass().getSimpleName() : message;
    }

    public boolean isEmpty() {
        return this.contents.isEmpty();
    }
}
