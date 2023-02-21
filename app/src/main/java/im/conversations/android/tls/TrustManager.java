package im.conversations.android.tls;

import android.content.Context;
import im.conversations.android.database.model.Account;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public class TrustManager implements X509TrustManager {

    private final Context context;
    private final Account account;

    public TrustManager(final Context context, final Account account) {
        this.context = context;
        this.account = account;
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
