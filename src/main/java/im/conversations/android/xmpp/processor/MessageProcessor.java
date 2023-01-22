package im.conversations.android.xmpp.processor;

import android.content.Context;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.function.Consumer;

public class MessageProcessor implements Consumer<Message> {

    public MessageProcessor(final Context context, final XmppConnection connection) {}

    @Override
    public void accept(final Message messagePacket) {}
}
