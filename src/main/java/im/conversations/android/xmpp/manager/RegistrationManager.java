package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.register.Register;
import im.conversations.android.xmpp.model.register.Remove;
import im.conversations.android.xmpp.model.stanza.Iq;

public class RegistrationManager extends AbstractManager {

    public RegistrationManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Void> setPassword(final String password) {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.SET);
        final var register = iq.addExtension(new Register());
        register.addUsername(account.address.getEscapedLocal());
        register.addPassword(password);
        return Futures.transform(
                connection.sendIqPacket(iq), r -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> unregister() {
        final var iq = new Iq(Iq.Type.SET);
        final var register = iq.addExtension(new Register());
        register.addExtension(new Remove());
        return Futures.transform(
                connection.sendIqPacket(iq), r -> null, MoreExecutors.directExecutor());
    }

    // TODO support registration
    // 3 possible responses:
    // 1) username + password
    // 2) Captcha as shown here: https://xmpp.org/extensions/xep-0158.html#register
    // 3) Redirection as show here: https://xmpp.org/extensions/xep-0077.html#redirect
}
