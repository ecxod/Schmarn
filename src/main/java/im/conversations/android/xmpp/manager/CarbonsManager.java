package im.conversations.android.xmpp.manager;

import android.content.Context;
import android.util.Log;
import eu.siacs.conversations.Config;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.carbons.Enable;
import im.conversations.android.xmpp.model.stanza.IQ;

public class CarbonsManager extends AbstractManager {

    private boolean enabled = false;

    public CarbonsManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void enable() {
        final var iq = new IQ(IQ.Type.SET);
        iq.addExtension(new Enable());
        connection.sendIqPacket(
                iq,
                result -> {
                    if (result.getType() == IQ.Type.RESULT) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().address + ": successfully enabled carbons");
                        this.enabled = true;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().address + ": could not enable carbons " + result);
                    }
                });
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }
}
