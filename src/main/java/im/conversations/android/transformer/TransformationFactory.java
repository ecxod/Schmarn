package im.conversations.android.transformer;

import android.content.Context;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;

public class TransformationFactory extends XmppConnection.Delegate {

    public TransformationFactory(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public Transformation create(final Message message, final String stanzaId) {
        return create(message, stanzaId, Instant.now());
    }

    public Transformation create(
            final Message message, final String stanzaId, final Instant receivedAt) {
        final var boundAddress = connection.getBoundAddress().asBareJid();
        final var from = message.getFrom();
        final var to = message.getTo();
        final Jid remote;
        if (from == null || from.asBareJid().equals(boundAddress)) {
            remote = to == null ? boundAddress : to;
        } else {
            remote = from;
        }
        final String occupantId;
        if (message.getType() == Message.Type.GROUPCHAT && message.hasExtension(OccupantId.class)) {
            if (from != null
                    && getManager(DiscoManager.class)
                            .hasFeature(from.asBareJid(), Namespace.OCCUPANT_ID)) {
                occupantId = message.getExtension(OccupantId.class).getId();
            } else {
                occupantId = null;
            }
        } else {
            occupantId = null;
        }
        return Transformation.of(message, receivedAt, remote, stanzaId, occupantId);
    }
}
