package im.conversations.android.xmpp.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;

public class ScramSha512Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-512-PLUS";

    public ScramSha512Plus(
            final Account account,
            final Credential credential,
            final ChannelBinding channelBinding) {
        super(account, credential, channelBinding);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return (key == null || key.length == 0)
                ? Hashing.hmacSha512(EMPTY_KEY)
                : Hashing.hmacSha512(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha512();
    }

    @Override
    public int getPriority() {
        return 45;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
