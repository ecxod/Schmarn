package im.conversations.android.xmpp.axolotl;

import com.google.common.base.Preconditions;
import eu.siacs.conversations.xmpp.Jid;
import org.whispersystems.libsignal.SignalProtocolAddress;

public class AxolotlAddress extends SignalProtocolAddress {

    private final Jid jid;

    public AxolotlAddress(final Jid jid, int deviceId) {
        super(jid.toEscapedString(), deviceId);
        Preconditions.checkArgument(jid.isBareJid(), "AxolotlAddresses must use bare JIDs");
        this.jid = jid;
    }

    public Jid getJid() {
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
