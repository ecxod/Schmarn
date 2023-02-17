package im.conversations.android.tls;

import java.security.KeyStore;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustManagers {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustManagers.class);

    public static X509TrustManager getTrustManager() {
        try {
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init((KeyStore) null);
            for (final TrustManager t : tmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) {
                    return (X509TrustManager) t;
                }
            }
            return null;
        } catch (final Exception e) {
            LOGGER.info("Could not get default Trust Manager");
            return null;
        }
    }
}
