package im.conversations.android.dns;

import android.app.Application;
import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
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
import im.conversations.android.database.model.Connection;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.jxmpp.jid.DomainJid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public class Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    public static final int DEFAULT_PORT_XMPP = 5222;

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERVICE = "_xmpp-client";

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(4);

    public static void init(final Application application) {
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

    public static List<ServiceRecord> fromHardCoded(final Connection connection) {
        return Collections.singletonList(
                new ServiceRecord(
                        null,
                        DNSName.from(connection.hostname),
                        connection.port,
                        connection.directTls,
                        0,
                        true));
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

    public static List<ServiceRecord> resolve(final String domain, final boolean validateHostname) {
        final List<ServiceRecord> ipResults = fromIpAddress(domain);
        if (ipResults.size() > 0) {
            return ipResults;
        }
        final ListenableFuture<List<ServiceRecord>> directTlsSrvRecords =
                Futures.submitAsync(() -> resolveSrv(domain, true, validateHostname), EXECUTOR);
        final ListenableFuture<List<ServiceRecord>> startTlsSrvRecords =
                Futures.submitAsync(() -> resolveSrv(domain, false, validateHostname), EXECUTOR);
        final ListenableFuture<List<ServiceRecord>> srvRecords =
                Futures.transform(
                        Futures.allAsList(directTlsSrvRecords, startTlsSrvRecords),
                        input -> {
                            final var list =
                                    input.stream()
                                            .flatMap(List::stream)
                                            .collect(Collectors.toList());
                            if (list.isEmpty()) {
                                throw new IllegalStateException("No SRV records found");
                            }
                            return list;
                        },
                        MoreExecutors.directExecutor());
        final ListenableFuture<List<ServiceRecord>> fallback =
                Futures.submit(() -> resolveNoSrvRecords(DNSName.from(domain), true), EXECUTOR);
        final var resultFuture =
                Futures.catchingAsync(
                        srvRecords,
                        Exception.class,
                        input -> fallback,
                        MoreExecutors.directExecutor());
        try {
            return Ordering.natural().sortedCopy(resultFuture.get());
        } catch (final Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<ServiceRecord> fromIpAddress(final String domain) {
        if (!IP.matches(domain)) {
            return Collections.emptyList();
        }
        final InetAddress ip;
        try {
            ip = InetAddress.getByName(domain);
        } catch (final UnknownHostException e) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                new ServiceRecord(ip, null, DEFAULT_PORT_XMPP, false, 0, false));
    }

    private static ListenableFuture<List<ServiceRecord>> resolveSrv(
            final String domain, final boolean directTls, final boolean validateHostname)
            throws IOException {
        final var dnsName =
                DNSName.from(
                        (directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERVICE) + "._tcp." + domain);
        final ResolverResult<SRV> result = resolveWithFallback(dnsName, validateHostname);
        final List<ListenableFuture<List<ServiceRecord>>> results = new ArrayList<>();
        for (final SRV record : result.getAnswersOrEmptySet()) {
            if (record.name.length() == 0 && record.priority == 0) {
                continue;
            }
            final var ipv4 = Futures.submit(() -> resolveIPv4(directTls, result, record), EXECUTOR);
            final var ipv6 =
                    Futures.submit(
                            () ->
                                    resolveIp(
                                            record,
                                            AAAA.class,
                                            result.isAuthenticData(),
                                            directTls),
                            EXECUTOR);
            results.add(ipv4);
            results.add(ipv6);
        }
        return Futures.transform(
                Futures.allAsList(results),
                input -> input.stream().flatMap(List::stream).collect(Collectors.toList()),
                MoreExecutors.directExecutor());
    }

    @NonNull
    private static List<ServiceRecord> resolveIPv4(
            boolean directTls, ResolverResult<SRV> result, SRV record) {
        final List<ServiceRecord> ipv4s =
                resolveIp(record, A.class, result.isAuthenticData(), directTls);
        if (ipv4s.isEmpty()) {
            return Collections.singletonList(
                    ServiceRecord.fromRecord(record, directTls, result.isAuthenticData()));
        } else {
            return ipv4s;
        }
    }

    private static <D extends InternetAddressRR> List<ServiceRecord> resolveIp(
            SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        final ImmutableList.Builder<ServiceRecord> builder = new ImmutableList.Builder<>();
        try {
            ResolverResult<D> results = resolveWithFallback(srv.name, type, authenticated);
            for (D record : results.getAnswersOrEmptySet()) {
                builder.add(
                        ServiceRecord.fromRecord(
                                srv,
                                directTls,
                                results.isAuthenticData() && authenticated,
                                record.getInetAddress()));
            }
        } catch (final Throwable t) {
            LOGGER.info("error resolving {}", type.getSimpleName(), t);
        }
        return builder.build();
    }

    private static List<ServiceRecord> resolveNoSrvRecords(DNSName dnsName, boolean includeCName) {
        List<ServiceRecord> results = new ArrayList<>();
        try {
            for (A a : resolveWithFallback(dnsName, A.class, false).getAnswersOrEmptySet()) {
                results.add(ServiceRecord.createDefault(dnsName, a.getInetAddress()));
            }
            for (AAAA aaaa :
                    resolveWithFallback(dnsName, AAAA.class, false).getAnswersOrEmptySet()) {
                results.add(ServiceRecord.createDefault(dnsName, aaaa.getInetAddress()));
            }
            if (results.size() == 0 && includeCName) {
                for (CNAME cname :
                        resolveWithFallback(dnsName, CNAME.class, false).getAnswersOrEmptySet()) {
                    results.addAll(resolveNoSrvRecords(cname.name, false));
                }
            }
        } catch (Throwable throwable) {
            LOGGER.info("Error resolving fallback records", throwable);
        }
        results.add(ServiceRecord.createDefault(dnsName));
        return results;
    }

    private static ResolverResult<SRV> resolveWithFallback(
            final DNSName dnsName, boolean validateHostname) throws IOException {
        return resolveWithFallback(dnsName, SRV.class, validateHostname);
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
}
