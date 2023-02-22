package im.conversations.android.tls;

import android.content.Context;
import im.conversations.android.AbstractAccountService;
import im.conversations.android.database.model.Account;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public class TrustManager extends AbstractAccountService implements X509TrustManager {

    public TrustManager(final Context context, final Account account) {
        super(context, account);
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
