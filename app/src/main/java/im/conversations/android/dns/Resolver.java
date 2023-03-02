package im.conversations.android.dns;

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import de.measite.minidns.AbstractDNSClient;
import de.measite.minidns.DNSCache;
import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSName;
import de.measite.minidns.Question;
import de.measite.minidns.Record;
import de.measite.minidns.cache.LRUCache;
import de.measite.minidns.dnssec.DNSSECResultNotAuthenticException;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;
import de.measite.minidns.hla.DnssecResolverApi;
import de.measite.minidns.hla.ResolverApi;
import de.measite.minidns.hla.ResolverResult;
import de.measite.minidns.iterative.ReliableDNSClient;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.CNAME;
import de.measite.minidns.record.Data;
import de.measite.minidns.record.InternetAddressRR;
import de.measite.minidns.record.SRV;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jxmpp.jid.DomainJid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    public static final int DEFAULT_PORT_XMPP = 5222;

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERVICE = "_xmpp-client";

    private static Context SERVICE;

    public static void init(final Application application) {
        SERVICE = application.getApplicationContext();
        DNSClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(application));
        final AbstractDNSClient client = ResolverApi.INSTANCE.getClient();
        if (client instanceof ReliableDNSClient) {
            disableHardcodedDnsServers((ReliableDNSClient) client);
        }
    }

    private static void disableHardcodedDnsServers(ReliableDNSClient reliableDNSClient) {
        try {
            final Field dnsClientField = ReliableDNSClient.class.getDeclaredField("dnsClient");
            dnsClientField.setAccessible(true);
            final DNSClient dnsClient = (DNSClient) dnsClientField.get(reliableDNSClient);
            if (dnsClient != null) {
                dnsClient.getDataSource().setTimeout(3000);
            }
            final Field useHardcodedDnsServers =
                    DNSClient.class.getDeclaredField("useHardcodedDnsServers");
            useHardcodedDnsServers.setAccessible(true);
            useHardcodedDnsServers.setBoolean(dnsClient, false);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Unable to disable hardcoded DNS servers", e);
        }
    }

    public static List<Result> fromHardCoded(final String hostname, final int port) {
        final Result result = new Result();
        result.hostname = DNSName.from(hostname);
        result.port = port;
        result.directTls = useDirectTls(port);
        result.authenticated = true;
        return Collections.singletonList(result);
    }

    public static void checkDomain(final DomainJid jid) {
        DNSName.from(jid.getDomain());
    }

    public static boolean invalidHostname(final String hostname) {
        try {
            DNSName.from(hostname);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    public static void clearCache() {
        final AbstractDNSClient client = ResolverApi.INSTANCE.getClient();
        final DNSCache dnsCache = client.getCache();
        if (dnsCache instanceof LRUCache) {
            LOGGER.debug("clearing DNS cache");
            ((LRUCache) dnsCache).clear();
        }
    }

    public static boolean useDirectTls(final int port) {
        return port == 443 || port == 5223;
    }

    public static List<Result> resolve(String domain) {
        final List<Result> ipResults = fromIpAddress(domain);
        if (ipResults.size() > 0) {
            return ipResults;
        }
        final List<Result> results = new ArrayList<>();
        final List<Result> fallbackResults = new ArrayList<>();
        final Thread[] threads = new Thread[3];
        threads[0] =
                new Thread(
                        () -> {
                            try {
                                final List<Result> list = resolveSrv(domain, true);
                                synchronized (results) {
                                    results.addAll(list);
                                }
                            } catch (final Throwable throwable) {
                                LOGGER.debug("error resolving SRV record (direct TLS)", throwable);
                            }
                        });
        threads[1] =
                new Thread(
                        () -> {
                            try {
                                final List<Result> list = resolveSrv(domain, false);
                                synchronized (results) {
                                    results.addAll(list);
                                }

                            } catch (Throwable throwable) {
                                LOGGER.debug(
                                        "error resolving SRV record (direct STARTTLS)", throwable);
                            }
                        });
        threads[2] =
                new Thread(
                        () -> {
                            List<Result> list = resolveNoSrvRecords(DNSName.from(domain), true);
                            synchronized (fallbackResults) {
                                fallbackResults.addAll(list);
                            }
                        });
        for (final Thread thread : threads) {
            thread.start();
        }
        try {
            threads[0].join();
            threads[1].join();
            if (results.size() > 0) {
                threads[2].interrupt();
                synchronized (results) {
                    Collections.sort(results);
                    LOGGER.info("{}", results);
                    return new ArrayList<>(results);
                }
            } else {
                threads[2].join();
                synchronized (fallbackResults) {
                    Collections.sort(fallbackResults);
                    LOGGER.info("fallback {}", fallbackResults);
                    return new ArrayList<>(fallbackResults);
                }
            }
        } catch (InterruptedException e) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
            return Collections.emptyList();
        }
    }

    private static List<Result> fromIpAddress(String domain) {
        if (!IP.matches(domain)) {
            return Collections.emptyList();
        }
        try {
            Result result = new Result();
            result.ip = InetAddress.getByName(domain);
            result.port = DEFAULT_PORT_XMPP;
            return Collections.singletonList(result);
        } catch (UnknownHostException e) {
            return Collections.emptyList();
        }
    }

    private static List<Result> resolveSrv(String domain, final boolean directTls)
            throws IOException {
        DNSName dnsName =
                DNSName.from(
                        (directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERVICE) + "._tcp." + domain);
        ResolverResult<SRV> result = resolveWithFallback(dnsName, SRV.class);
        final List<Result> results = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        for (SRV record : result.getAnswersOrEmptySet()) {
            if (record.name.length() == 0 && record.priority == 0) {
                continue;
            }
            threads.add(
                    new Thread(
                            () -> {
                                final List<Result> ipv4s =
                                        resolveIp(
                                                record,
                                                A.class,
                                                result.isAuthenticData(),
                                                directTls);
                                if (ipv4s.size() == 0) {
                                    Result resolverResult = Result.fromRecord(record, directTls);
                                    resolverResult.authenticated = result.isAuthenticData();
                                    ipv4s.add(resolverResult);
                                }
                                synchronized (results) {
                                    results.addAll(ipv4s);
                                }
                            }));
            threads.add(
                    new Thread(
                            () -> {
                                final List<Result> ipv6s =
                                        resolveIp(
                                                record,
                                                AAAA.class,
                                                result.isAuthenticData(),
                                                directTls);
                                synchronized (results) {
                                    results.addAll(ipv6s);
                                }
                            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                return Collections.emptyList();
            }
        }
        return results;
    }

    private static <D extends InternetAddressRR> List<Result> resolveIp(
            SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        List<Result> list = new ArrayList<>();
        try {
            ResolverResult<D> results = resolveWithFallback(srv.name, type, authenticated);
            for (D record : results.getAnswersOrEmptySet()) {
                Result resolverResult = Result.fromRecord(srv, directTls);
                resolverResult.authenticated =
                        results.isAuthenticData()
                                && authenticated; // TODO technically it doesn’t matter if the IP
                // was authenticated
                resolverResult.ip = record.getInetAddress();
                list.add(resolverResult);
            }
        } catch (final Throwable t) {
            LOGGER.info("error resolving {}", type.getSimpleName(), t);
        }
        return list;
    }

    private static List<Result> resolveNoSrvRecords(DNSName dnsName, boolean withCnames) {
        List<Result> results = new ArrayList<>();
        try {
            for (A a : resolveWithFallback(dnsName, A.class, false).getAnswersOrEmptySet()) {
                results.add(Result.createDefault(dnsName, a.getInetAddress()));
            }
            for (AAAA aaaa :
                    resolveWithFallback(dnsName, AAAA.class, false).getAnswersOrEmptySet()) {
                results.add(Result.createDefault(dnsName, aaaa.getInetAddress()));
            }
            if (results.size() == 0 && withCnames) {
                for (CNAME cname :
                        resolveWithFallback(dnsName, CNAME.class, false).getAnswersOrEmptySet()) {
                    results.addAll(resolveNoSrvRecords(cname.name, false));
                }
            }
        } catch (Throwable throwable) {
            LOGGER.info("Error resolving fallback records", throwable);
        }
        results.add(Result.createDefault(dnsName));
        return results;
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(
            DNSName dnsName, Class<D> type) throws IOException {
        return resolveWithFallback(dnsName, type, validateHostname());
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(
            DNSName dnsName, Class<D> type, boolean validateHostname) throws IOException {
        final Question question = new Question(dnsName, Record.TYPE.getType(type));
        if (!validateHostname) {
            return ResolverApi.INSTANCE.resolve(question);
        }
        try {
            return DnssecResolverApi.INSTANCE.resolveDnssecReliable(question);
        } catch (DNSSECResultNotAuthenticException e) {
            LOGGER.info(
                    "Error resolving {} with DNSSEC. Trying DNS instead", type.getSimpleName(), e);
        } catch (IOException e) {
            throw e;
        } catch (Throwable throwable) {
            LOGGER.info(
                    "Error resolving {} with DNSSEC. Trying DNS instead",
                    type.getSimpleName(),
                    throwable);
        }
        return ResolverApi.INSTANCE.resolve(question);
    }

    private static boolean validateHostname() {
        // TODO bring back in one form or another
        return false;
    }

    public static class Result implements Comparable<Result> {
        private InetAddress ip;
        private DNSName hostname;
        private int port = DEFAULT_PORT_XMPP;
        private boolean directTls = false;
        private boolean authenticated = false;
        private int priority;

        static Result fromRecord(SRV srv, boolean directTls) {
            Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.name;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        static Result createDefault(DNSName hostname, InetAddress ip) {
            Result result = new Result();
            result.port = DEFAULT_PORT_XMPP;
            result.hostname = hostname;
            result.ip = ip;
            return result;
        }

        static Result createDefault(DNSName hostname) {
            return createDefault(hostname, null);
        }

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
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
        public int compareTo(@NonNull Result result) {
            // TODO use comparison chain. get rid of IPv4 preference
            if (result.priority == priority) {
                if (directTls == result.directTls) {
                    if (ip == null && result.ip == null) {
                        return 0;
                    } else if (ip != null && result.ip != null) {
                        if (ip instanceof Inet4Address && result.ip instanceof Inet4Address) {
                            return 0;
                        } else {
                            return ip instanceof Inet4Address ? -1 : 1;
                        }
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
            Result result = (Result) o;
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
}
