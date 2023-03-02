package im.conversations.android.xmpp.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.ListenableFuture;
import im.conversations.android.AppSettings;
import im.conversations.android.tls.TrustManagers;
import im.conversations.android.xmpp.XmppConnection;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressLint("CustomX509TrustManager")
public class TrustManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrustManager.class);

    private final AppSettings appSettings;

    private Function<ScopeFingerprint, ListenableFuture<Boolean>> userInterfaceCallback;

    public TrustManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.appSettings = new AppSettings(context);
    }

    private boolean isServerTrustedInSystemCaStore(
            final X509Certificate[] chain, final String authType) {
        final var trustManager = TrustManagers.getTrustManager();
        if (trustManager == null) {
            return false;
        }
        try {
            trustManager.checkServerTrusted(chain, authType);
            return true;
        } catch (final CertificateException e) {
            return false;
        }
    }

    public static String fingerprint(final byte[] bytes) {
        return fingerprint(bytes, bytes.length);
    }

    public static String fingerprint(final byte[] bytes, final int segments) {
        return Joiner.on('\n')
                .join(
                        Lists.transform(
                                Lists.transform(
                                        Lists.partition(Bytes.asList(bytes), segments),
                                        s -> Lists.transform(s, b -> String.format("%02X", b))),
                                hex -> Joiner.on(':').join(hex)));
    }

    public void setUserInterfaceCallback(
            final Function<ScopeFingerprint, ListenableFuture<Boolean>> callback) {
        this.userInterfaceCallback = callback;
    }

    public void removeUserInterfaceCallback(
            final Function<ScopeFingerprint, ListenableFuture<Boolean>> callback) {
        if (this.userInterfaceCallback == callback) {
            LOGGER.info("Remove user interface callback");
            this.userInterfaceCallback = null;
        }
    }

    public X509TrustManager scopedTrustManager(final String scope) {
        return new ScopedTrustManager(scope);
    }

    public static class ScopeFingerprint {
        public final String scope;
        public final ByteBuffer fingerprint;

        public ScopeFingerprint(final String scope, final byte[] fingerprint) {
            this(scope, ByteBuffer.wrap(fingerprint));
        }

        public ScopeFingerprint(final String scope, final ByteBuffer fingerprint) {
            this.scope = scope;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScopeFingerprint that = (ScopeFingerprint) o;
            return Objects.equal(scope, that.scope) && Objects.equal(fingerprint, that.fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(scope, fingerprint);
        }
    }

    private class ScopedTrustManager implements X509TrustManager {

        private final String scope;

        private ScopedTrustManager(final String scope) {
            this.scope = scope;
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            throw new CertificateException(
                    "This implementation has no support for client certificates");
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                throws CertificateException {
            if (chain.length == 0) {
                throw new CertificateException("Certificate chain is zero length");
            }
            for (final X509Certificate certificate : chain) {
                certificate.checkValidity();
            }
            final boolean isTrustSystemCaStore = appSettings.isTrustSystemCAStore();
            if (isTrustSystemCaStore && isServerTrustedInSystemCaStore(chain, authType)) {
                return;
            }
            final X509Certificate certificate = chain[0];
            final byte[] fingerprint =
                    Hashing.sha256().hashBytes(certificate.getEncoded()).asBytes();
            final var scopeFingerprint = new ScopeFingerprint(scope, fingerprint);
            LOGGER.info("Looking up {} in database", fingerprint(fingerprint));
            final var callback = TrustManager.this.userInterfaceCallback;
            if (callback == null) {
                throw new CertificateException(
                        "No user interface registered. Can not trust certificate");
            }
            final ListenableFuture<Boolean> futureDecision = callback.apply(scopeFingerprint);
            final boolean decision;
            try {
                decision = Boolean.TRUE.equals(futureDecision.get(10, TimeUnit.SECONDS));
            } catch (final ExecutionException | InterruptedException | TimeoutException e) {
                futureDecision.cancel(true);
                throw new CertificateException(
                        "Timeout waiting for user response", Throwables.getRootCause(e));
            }
            if (decision) {
                return;
            }
            throw new CertificateException("User did not trust certificate");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
