package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.Consumer;

public class PresenceProcessor implements Consumer<PresencePacket> {

    public PresenceProcessor(final Context context, final XmppConnection connection) {}

    @Override
    public void accept(PresencePacket presencePacket) {}
}
