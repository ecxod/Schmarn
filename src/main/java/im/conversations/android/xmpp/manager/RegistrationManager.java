package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.register.Instructions;
import im.conversations.android.xmpp.model.register.Password;
import im.conversations.android.xmpp.model.register.Register;
import im.conversations.android.xmpp.model.register.Remove;
import im.conversations.android.xmpp.model.register.Username;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.regex.Matcher;

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

    public ListenableFuture<Registration> getRegistration() {
        final var iq = new Iq(Iq.Type.SET);
        iq.addExtension(new Register());
        return Futures.transform(
                connection.sendIqPacket(iq),
                result -> {
                    final var register = result.getExtension(Register.class);
                    if (register == null) {
                        throw new IllegalStateException(
                                "Server did not include register in response");
                    }
                    if (register.hasExtension(Username.class)
                            && register.hasExtension(Password.class)) {
                        return new SimpleRegistration();
                    }
                    final var data = register.getExtension(Data.class);
                    if (data != null && Namespace.REGISTER.equals(data.getFormType())) {
                        return new ExtendedRegistration(data);
                    }
                    final var oob = register.getExtension(OutOfBandData.class);
                    final var instructions = register.getExtension(Instructions.class);
                    final String instructionsText =
                            instructions == null ? null : instructions.getContent();
                    final String redirectUrl = oob == null ? null : oob.getURL();
                    if (redirectUrl != null) {
                        return new RedirectRegistration(redirectUrl);
                    }
                    if (instructionsText != null) {
                        final Matcher matcher = Patterns.WEB_URL.matcher(instructionsText);
                        if (matcher.find()) {
                            final String instructionsUrl =
                                    instructionsText.substring(matcher.start(), matcher.end());
                            return new RedirectRegistration(instructionsUrl);
                        }
                    }
                    throw new IllegalStateException("No supported registration method found");
                },
                MoreExecutors.directExecutor());
    }

    private abstract static class Registration {}

    // only requires Username + Password
    public static class SimpleRegistration extends Registration {}

    // Captcha as shown here: https://xmpp.org/extensions/xep-0158.html#register
    public static class ExtendedRegistration extends Registration {
        private final Data data;

        public ExtendedRegistration(Data data) {
            this.data = data;
        }

        public Data getData() {
            return this.data;
        }
    }

    // Redirection as show here: https://xmpp.org/extensions/xep-0077.html#redirect
    public static class RedirectRegistration extends Registration {
        private final String url;

        public RedirectRegistration(@NonNull final String url) {
            this.url = url;
        }

        public @NonNull String getURL() {
            return this.url;
        }
    }
}
