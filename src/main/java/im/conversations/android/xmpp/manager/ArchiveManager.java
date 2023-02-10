package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Preconditions;
import im.conversations.android.transformer.TransformationFactory;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.delay.Delay;
import im.conversations.android.xmpp.model.mam.Result;
import im.conversations.android.xmpp.model.stanza.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveManager.class);

    private final TransformationFactory transformationFactory;

    public ArchiveManager(Context context, XmppConnection connection) {
        super(context, connection);
        this.transformationFactory = new TransformationFactory(context, connection);
    }

    public void handle(final Message message) {
        final var result = message.getExtension(Result.class);
        Preconditions.checkArgument(result != null, "The message needs to contain a MAM result");
        final var from = message.getFrom();
        final var stanzaId = result.getId();
        final var queryId = result.getQueryId();
        final var forwarded = result.getForwarded();
        if (forwarded == null || queryId == null || stanzaId == null) {
            LOGGER.info("Received invalid MAM result from {} ", from);
            return;
        }
        final var forwardedMessage = forwarded.getMessage();
        final var delay = forwarded.getExtension(Delay.class);
        final var receivedAt = delay == null ? null : delay.getStamp();
        if (forwardedMessage == null || receivedAt == null) {
            LOGGER.info("MAM result from {} is missing message or receivedAt (delay)", from);
            return;
        }
        // TODO get query based on queryId and from

        final var transformation = this.transformationFactory.create(message, stanzaId, receivedAt);

        // TODO create transformation; add transformation to Query.Transformer
    }
}
