package im.conversations.android.xmpp.axolotl;

import org.jxmpp.jid.BareJid;
import org.whispersystems.libsignal.SignalProtocolAddress;

public class AxolotlAddress extends SignalProtocolAddress {

    private final BareJid jid;

    public AxolotlAddress(final BareJid jid, int deviceId) {
        super(jid.toString(), deviceId);
        this.jid = jid;
    }

    public BareJid getJid() {
        return this.jid;
    }

    public static AxolotlAddress cast(final SignalProtocolAddress signalProtocolAddress) {
        if (signalProtocolAddress instanceof AxolotlAddress) {
            return (AxolotlAddress) signalProtocolAddress;
        }
        throw new IllegalArgumentException(
                String.format(
                        "This %s is not a %s",
                        SignalProtocolAddress.class.getSimpleName(),
                        AxolotlAddress.class.getSimpleName()));
    }
}
