package im.conversations.android.xmpp.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;

public class ScramSha1Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-1-PLUS";

    public ScramSha1Plus(
            final Account account, Credential credential, final ChannelBinding channelBinding) {
        super(account, credential, channelBinding);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return (key == null || key.length == 0)
                ? Hashing.hmacSha1(EMPTY_KEY)
                : Hashing.hmacSha1(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha1();
    }

    @Override
    public int getPriority() {
        return 35; // higher than SCRAM-SHA512 (30)
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
