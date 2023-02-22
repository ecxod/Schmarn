package im.conversations.android.axolotl;

import java.nio.charset.StandardCharsets;
import org.whispersystems.libsignal.IdentityKey;

public class AxolotlPayload {

    public final AxolotlAddress axolotlAddress;
    public final IdentityKey identityKey;
    public final boolean preKeyMessage;
    public final byte[] payload;

    public AxolotlPayload(
            AxolotlAddress axolotlAddress,
            final IdentityKey identityKey,
            final boolean preKeyMessage,
            byte[] payload) {
        this.axolotlAddress = axolotlAddress;
        this.identityKey = identityKey;
        this.preKeyMessage = preKeyMessage;
        this.payload = payload;
    }

    public String payloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }
}
