package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Preconditions;
import im.conversations.android.transformer.Transformation;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.mam.Result;
import im.conversations.android.xmpp.model.stanza.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveManager.class);

    public ArchiveManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handle(final Message message) {
        final var result = message.getExtension(Result.class);
        Preconditions.checkArgument(result != null, "The message needs to contain a MAM result");
        final var from = message.getFrom();
        final var stanzaId = result.getId();
        final var queryId = result.getQueryId();
        final var forwarded = result.getForwarded();
        final var forwardedMessage = forwarded == null ? null : forwarded.getMessage();
        if (forwardedMessage == null || queryId == null || stanzaId == null) {
            LOGGER.info("Received invalid MAM result from {} ", from);
            return;
        }
        // TODO get query based on queryId and from

        final var transformation = Transformation.of(forwardedMessage, stanzaId);

        // TODO create transformation; add transformation to Query.Transformer
    }
}
