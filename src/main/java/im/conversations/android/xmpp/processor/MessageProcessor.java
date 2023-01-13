package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.Consumer;

public class MessageProcessor implements Consumer<MessagePacket> {

    public MessageProcessor(final Context context, final XmppConnection connection) {}

    @Override
    public void accept(final MessagePacket messagePacket) {}
}
