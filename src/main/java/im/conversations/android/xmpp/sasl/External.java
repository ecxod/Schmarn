package im.conversations.android.xmpp.sasl;

import android.util.Base64;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;
import javax.net.ssl.SSLSocket;

public class External extends SaslMechanism {

    public static final String MECHANISM = "EXTERNAL";

    public External(final Account account) {
        super(account, Credential.empty());
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        return Base64.encodeToString(account.address.toEscapedString().getBytes(), Base64.NO_WRAP);
    }
}
