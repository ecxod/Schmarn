package im.conversations.android.xmpp.sasl;

import android.util.Base64;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Credential;
import java.nio.charset.Charset;
import javax.net.ssl.SSLSocket;

public class Plain extends SaslMechanism {

    public static final String MECHANISM = "PLAIN";

    public Plain(final Account account, final Credential credential) {
        super(account, credential);
    }

    public static String getMessage(String username, String password) {
        final String message = '\u0000' + username + '\u0000' + password;
        return Base64.encodeToString(message.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        return getMessage(account.address.getEscapedLocal(), credential.password);
    }
}
