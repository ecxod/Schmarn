package im.conversations.android.axolotl;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.state.SignalProtocolStore;

public class AxolotlSession {

    public final AxolotlAddress axolotlAddress;
    public final IdentityKey identityKey;
    public final SessionCipher sessionCipher;

    private AxolotlSession(
            AxolotlAddress axolotlAddress,
            final IdentityKey identityKey,
            SessionCipher sessionCipher) {
        this.axolotlAddress = axolotlAddress;
        this.identityKey = identityKey;
        this.sessionCipher = sessionCipher;
    }

    public static AxolotlSession of(
            final SignalProtocolStore signalProtocolStore,
            final IdentityKey identityKey,
            final AxolotlAddress axolotlAddress) {
        final var sessionCipher = new SessionCipher(signalProtocolStore, axolotlAddress);
        return new AxolotlSession(axolotlAddress, identityKey, sessionCipher);
    }
}
