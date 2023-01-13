package im.conversations.android.xmpp.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;

public class HashedTokenSha512 extends HashedToken {

    public HashedTokenSha512(
            final Account account,
            final Credential credential,
            final ChannelBinding channelBinding) {
        super(account, credential, channelBinding);
    }

    @Override
    protected HashFunction getHashFunction(final byte[] key) {
        return Hashing.hmacSha512(key);
    }

    @Override
    public Mechanism getTokenMechanism() {
        return new Mechanism("SHA-512", this.channelBinding);
    }
}
