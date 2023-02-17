package im.conversations.android.xmpp.sasl;

import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;
import javax.net.ssl.SSLSocket;

public abstract class ScramPlusMechanism extends ScramMechanism implements ChannelBindingMechanism {

    ScramPlusMechanism(
            Account account, final Credential credential, ChannelBinding channelBinding) {
        super(account, credential, channelBinding);
    }

    @Override
    protected byte[] getChannelBindingData(final SSLSocket sslSocket)
            throws AuthenticationException {
        return ChannelBindingMechanism.getChannelBindingData(sslSocket, this.channelBinding);
    }

    @Override
    public ChannelBinding getChannelBinding() {
        return this.channelBinding;
    }
}
