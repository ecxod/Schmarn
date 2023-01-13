package im.conversations.android.xmpp.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;

public class HashedTokenSha256 extends HashedToken {

    public HashedTokenSha256(
            final Account account,
            final Credential credential,
            final ChannelBinding channelBinding) {
        super(account, credential, channelBinding);
    }

    @Override
    protected HashFunction getHashFunction(final byte[] key) {
        return Hashing.hmacSha256(key);
    }

    @Override
    public Mechanism getTokenMechanism() {
        return new Mechanism("SHA-256", channelBinding);
    }
}
