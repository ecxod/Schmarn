package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.Consumer;

public class JingleProcessor implements Consumer<JinglePacket> {

    public JingleProcessor(final Context context, final XmppConnection connection) {}

    @Override
    public void accept(JinglePacket jinglePacket) {}
}
