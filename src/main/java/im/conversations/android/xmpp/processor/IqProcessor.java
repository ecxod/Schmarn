package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.Consumer;

public class IqProcessor implements Consumer<IqPacket> {

    public IqProcessor(final Context context, final XmppConnection connection) {}

    @Override
    public void accept(final IqPacket packet) {}
}
