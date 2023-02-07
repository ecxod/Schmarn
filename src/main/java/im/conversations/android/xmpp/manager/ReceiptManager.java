package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;
import im.conversations.android.xmpp.model.markers.Markable;
import im.conversations.android.xmpp.model.receipts.Received;
import im.conversations.android.xmpp.model.receipts.Request;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Collection;

public class ReceiptManager extends AbstractManager {

    public ReceiptManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void received(
            final Jid from,
            final String id,
            Collection<DeliveryReceiptRequest> deliveryReceiptRequests) {
        if (deliveryReceiptRequests.isEmpty() || Strings.isNullOrEmpty(id)) {
            return;
        }
        // TODO check roster
        final Message response = new Message();
        response.setTo(from);
        for (final DeliveryReceiptRequest request : deliveryReceiptRequests) {
            if (request instanceof Request) {
                final var received = response.addExtension(new Received());
                received.setId(id);
            } else if (request instanceof Markable) {
                final var received =
                        response.addExtension(
                                new im.conversations.android.xmpp.model.markers.Received());
                received.setId(id);
            }
        }
        connection.sendMessagePacket(response);
    }
}
