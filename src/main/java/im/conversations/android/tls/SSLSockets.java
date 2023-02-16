package im.conversations.android.tls;

import android.os.Build;
import androidx.annotation.RequiresApi;
import com.google.common.base.Strings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLSockets {

    private static final String[] ENABLED_CIPHERS = {
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA384",
        "TLS_DHE_RSA_WITH_AES_256_GCM_SHA256",
        "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_DHE_RSA_WITH_CAMELLIA_256_SHA",

        // Fallback.
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_GCM_SHA384",
        "TLS_RSA_WITH_AES_256_GCM_SHA256",
        "TLS_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_128_CBC_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA384",
        "TLS_RSA_WITH_AES_256_CBC_SHA256",
        "TLS_RSA_WITH_AES_256_CBC_SHA384",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
    };

    private static final String[] WEAK_CIPHER_PATTERNS = {
        "_NULL_", "_EXPORT_", "_anon_", "_RC4_", "_DES_", "_MD5",
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSockets.class);

    public static void setSecurity(final SSLSocket sslSocket) {
        final String[] supportProtocols;
        final Collection<String> supportedProtocols =
                new LinkedList<>(Arrays.asList(sslSocket.getSupportedProtocols()));
        supportedProtocols.remove("SSLv3");
        supportProtocols = supportedProtocols.toArray(new String[0]);

        sslSocket.setEnabledProtocols(supportProtocols);

        final String[] cipherSuites = getOrderedCipherSuites(sslSocket.getSupportedCipherSuites());
        if (cipherSuites.length > 0) {
            sslSocket.setEnabledCipherSuites(cipherSuites);
        }
    }

    public static String[] getOrderedCipherSuites(final String[] platformSupportedCipherSuites) {
        final Collection<String> cipherSuites = new LinkedHashSet<>(Arrays.asList(ENABLED_CIPHERS));
        final List<String> platformCiphers = Arrays.asList(platformSupportedCipherSuites);
        cipherSuites.retainAll(platformCiphers);
        cipherSuites.addAll(platformCiphers);
        filterWeakCipherSuites(cipherSuites);
        cipherSuites.remove("TLS_FALLBACK_SCSV");
        return cipherSuites.toArray(new String[0]);
    }

    private static void filterWeakCipherSuites(final Collection<String> cipherSuites) {
        final Iterator<String> it = cipherSuites.iterator();
        while (it.hasNext()) {
            String cipherName = it.next();
            // remove all ciphers with no or very weak encryption or no authentication
            for (final String weakCipherPattern : WEAK_CIPHER_PATTERNS) {
                if (cipherName.contains(weakCipherPattern)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    public static void setHostname(final SSLSocket socket, final String hostname) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setHostname(socket, hostname);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setHostnameNougat(socket, hostname);
        } else {
            setHostnameReflection(socket, hostname);
        }
    }

    private static void setHostnameReflection(final SSLSocket socket, final String hostname) {
        try {
            socket.getClass().getMethod("setHostname", String.class).invoke(socket, hostname);
        } catch (final IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            LOGGER.warn("Could not set SNI hostname on socket", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static void setHostnameNougat(final SSLSocket socket, final String hostname) {
        final SSLParameters parameters = new SSLParameters();
        parameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        socket.setSSLParameters(parameters);
    }

    private static void setApplicationProtocolReflection(
            final SSLSocket socket, final String protocol) {
        try {
            final Method method = socket.getClass().getMethod("setAlpnProtocols", byte[].class);
            // the concatenation of 8-bit, length prefixed protocol names, just one in our case...
            // http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
            final byte[] protocolUTF8Bytes = protocol.getBytes(StandardCharsets.UTF_8);
            final byte[] lengthPrefixedProtocols = new byte[protocolUTF8Bytes.length + 1];
            lengthPrefixedProtocols[0] = (byte) protocol.length(); // cannot be over 255 anyhow
            System.arraycopy(
                    protocolUTF8Bytes, 0, lengthPrefixedProtocols, 1, protocolUTF8Bytes.length);
            method.invoke(socket, new Object[] {lengthPrefixedProtocols});
        } catch (final IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            LOGGER.warn("Could not set application protocol on socket", e);
        }
    }

    public static void setApplicationProtocol(final SSLSocket socket, final String protocol) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setApplicationProtocols(socket, new String[] {protocol});
        } else {
            setApplicationProtocolReflection(socket, protocol);
        }
    }

    public static SSLContext getSSLContext() throws NoSuchAlgorithmException {
        try {
            return SSLContext.getInstance("TLSv1.3");
        } catch (NoSuchAlgorithmException e) {
            return SSLContext.getInstance("TLSv1.2");
        }
    }

    public static Version version(final Socket socket) {
        if (socket instanceof SSLSocket) {
            final SSLSocket sslSocket = (SSLSocket) socket;
            return Version.of(sslSocket.getSession().getProtocol());
        } else {
            return Version.NONE;
        }
    }

    public enum Version {
        TLS_1_0,
        TLS_1_1,
        TLS_1_2,
        TLS_1_3,
        UNKNOWN,
        NONE;

        private static Version of(final String protocol) {
            switch (Strings.nullToEmpty(protocol)) {
                case "TLSv1":
                    return TLS_1_0;
                case "TLSv1.1":
                    return TLS_1_1;
                case "TLSv1.2":
                    return TLS_1_2;
                case "TLSv1.3":
                    return TLS_1_3;
                default:
                    return UNKNOWN;
            }
        }
    }
}
