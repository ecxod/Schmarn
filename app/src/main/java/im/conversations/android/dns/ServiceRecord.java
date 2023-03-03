package im.conversations.android.dns;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import de.measite.minidns.DNSName;
import de.measite.minidns.record.SRV;
import java.net.InetAddress;

public class ServiceRecord implements Comparable<ServiceRecord> {
    private final InetAddress ip;
    private final DNSName hostname;
    private final int port;
    private final boolean directTls;
    private final int priority;
    private final boolean authenticated;

    public ServiceRecord(
            InetAddress ip,
            DNSName hostname,
            int port,
            boolean directTls,
            int priority,
            boolean authenticated) {
        this.ip = ip;
        this.hostname = hostname;
        this.port = port;
        this.directTls = directTls;
        this.authenticated = authenticated;
        this.priority = priority;
    }

    public static ServiceRecord fromRecord(
            final SRV srv,
            final boolean directTls,
            final boolean authenticated,
            final InetAddress ip) {
        return new ServiceRecord(ip, srv.name, srv.port, directTls, srv.priority, authenticated);
    }

    public static ServiceRecord fromRecord(
            final SRV srv, final boolean directTls, final boolean authenticated) {
        return fromRecord(srv, directTls, authenticated, null);
    }

    static ServiceRecord createDefault(final DNSName hostname, final InetAddress ip) {
        return new ServiceRecord(ip, hostname, Resolver.DEFAULT_PORT_XMPP, false, 0, false);
    }

    static ServiceRecord createDefault(final DNSName hostname) {
        return createDefault(hostname, null);
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public int getPriority() {
        return this.priority;
    }

    public DNSName getHostname() {
        return hostname;
    }

    public boolean isDirectTls() {
        return directTls;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public int compareTo(@NonNull ServiceRecord result) {
        if (result.priority == priority) {
            if (directTls == result.directTls) {
                if (ip == null && result.ip == null) {
                    return 0;
                } else {
                    return ip != null ? -1 : 1;
                }
            } else {
                return directTls ? -1 : 1;
            }
        } else {
            return priority - result.priority;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceRecord result = (ServiceRecord) o;
        return port == result.port
                && directTls == result.directTls
                && authenticated == result.authenticated
                && priority == result.priority
                && Objects.equal(ip, result.ip)
                && Objects.equal(hostname, result.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(ip, hostname, port, directTls, authenticated, priority);
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ip", ip)
                .add("hostname", hostname)
                .add("port", port)
                .add("directTls", directTls)
                .add("authenticated", authenticated)
                .add("priority", priority)
                .toString();
    }
}
