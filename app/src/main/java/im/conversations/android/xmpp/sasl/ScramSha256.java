package im.conversations.android.xmpp.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;

public class ScramSha256 extends ScramMechanism {

    public static final String MECHANISM = "SCRAM-SHA-256";

    public ScramSha256(final Account account, final Credential credential) {
        super(account, credential, ChannelBinding.NONE);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return (key == null || key.length == 0)
                ? Hashing.hmacSha256(EMPTY_KEY)
                : Hashing.hmacSha256(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha256();
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
