package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.AppSettings;
import im.conversations.android.xmpp.XmppConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public class TrustManager extends AbstractManager implements X509TrustManager {

    private final AppSettings appSettings;

    public TrustManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.appSettings = new AppSettings(context);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {}

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
