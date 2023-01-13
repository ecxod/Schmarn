package im.conversations.android.xmpp.sasl;

import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;
import javax.net.ssl.SSLSocket;

public class Anonymous extends SaslMechanism {

    public static final String MECHANISM = "ANONYMOUS";

    public Anonymous(final Account account) {
        super(account, Credential.empty());
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        return "";
    }
}
