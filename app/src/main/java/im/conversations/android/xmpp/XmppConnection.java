package im.conversations.android.xmpp;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import im.conversations.android.BuildConfig;
import im.conversations.android.Conversations;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Connection;
import im.conversations.android.database.model.Credential;
import im.conversations.android.dns.Resolver;
import im.conversations.android.socks.SocksSocketFactory;
import im.conversations.android.tls.SSLSockets;
import im.conversations.android.tls.TrustManagers;
import im.conversations.android.tls.XmppDomainVerifier;
import im.conversations.android.util.PendingItem;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xml.Tag;
import im.conversations.android.xml.TagWriter;
import im.conversations.android.xml.XmlReader;
import im.conversations.android.xmpp.manager.AbstractManager;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.bind2.BindInlineFeatures;
import im.conversations.android.xmpp.model.csi.Active;
import im.conversations.android.xmpp.model.csi.Inactive;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.ping.Ping;
import im.conversations.android.xmpp.model.sasl2.Inline;
import im.conversations.android.xmpp.model.sm.Ack;
import im.conversations.android.xmpp.model.sm.Enable;
import im.conversations.android.xmpp.model.sm.Request;
import im.conversations.android.xmpp.model.sm.Resume;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.stanza.Stanza;
import im.conversations.android.xmpp.model.streams.Features;
import im.conversations.android.xmpp.processor.BindProcessor;
import im.conversations.android.xmpp.processor.IqProcessor;
import im.conversations.android.xmpp.processor.MessageAcknowledgeProcessor;
import im.conversations.android.xmpp.processor.MessageProcessor;
import im.conversations.android.xmpp.processor.PresenceProcessor;
import im.conversations.android.xmpp.sasl.ChannelBinding;
import im.conversations.android.xmpp.sasl.HashedToken;
import im.conversations.android.xmpp.sasl.SaslMechanism;
import java.io.IOException;
import java.net.ConnectException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.HttpUrl;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

public class XmppConnection implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmppConnection.class);

    private static final boolean EXTENDED_SM_LOGGING = false;
    private static final int CONNECT_DISCO_TIMEOUT = 20;

    private final Context context;
    private final Account account;

    private final SparseArray<Stanza> mStanzaQueue = new SparseArray<>();
    private final Hashtable<String, Pair<Iq, Consumer<Iq>>> packetCallbacks = new Hashtable<>();
    private Socket socket;
    private XmlReader tagReader;
    private TagWriter tagWriter = new TagWriter();

    private boolean encryptionEnabled = false;
    private boolean shouldAuthenticate = true;
    private boolean inSmacksSession = false;
    private boolean quickStartInProgress = false;
    private boolean isBound = false;
    private Features streamFeatures;
    private String streamId = null;
    private Jid connectionAddress;
    private ConnectionState connectionState = ConnectionState.OFFLINE;
    private ConnectionState recentErrorConnectionState = ConnectionState.OFFLINE;
    private int stanzasReceived = 0;
    private int stanzasSent = 0;
    private int stanzasSentBeforeAuthentication;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private final AtomicBoolean mWaitingForSmCatchup = new AtomicBoolean(false);
    private final AtomicInteger mSmCatchupMessageCounter = new AtomicInteger(0);
    private int attempt = 0;
    private final Consumer<Presence> presencePacketConsumer;
    private final Consumer<Iq> iqPacketConsumer;
    private final Consumer<Message> messagePacketConsumer;
    private final BiFunction<Jid, String, Boolean> messageAcknowledgeProcessor;
    private final Consumer<Jid> bindConsumer;
    private final ClassToInstanceMap<AbstractManager> managers;
    private Consumer<XmppConnection> statusListener = null;
    private final PendingItem<SettableFuture<XmppConnection>> connectedFuture = new PendingItem<>();
    private SaslMechanism saslMechanism;
    private HashedToken.Mechanism hashTokenRequest;
    private HttpUrl redirectionUrl = null;
    private String verifiedHostname = null;
    private volatile Thread mThread;
    private CountDownLatch mStreamCountDownLatch;

    public XmppConnection(final Context context, final Account account) {
        this.context = context;
        this.account = account;
        this.connectionAddress = account.address;

        // these consumers are pure listeners; they don’t have public method except for accept|apply
        // those consumers don’t need to be invoked from anywhere except this connection
        // this is different to 'Managers' (like MAM, OMEMO, Avatar) that need to listen to external
        // events like 'go fetch history for x'
        this.messagePacketConsumer = new MessageProcessor(context, this);
        this.presencePacketConsumer = new PresenceProcessor(context, this);
        this.iqPacketConsumer = new IqProcessor(context, this);
        this.messageAcknowledgeProcessor = new MessageAcknowledgeProcessor(context, this);
        this.bindConsumer = new BindProcessor(context, this);
        this.managers = Managers.initialize(context, this);
    }

    public Account getAccount() {
        return account;
    }

    public <T extends AbstractManager> T getManager(Class<T> type) {
        return this.managers.getInstance(type);
    }

    private String fixResource(final String resource) {
        if (Strings.isNullOrEmpty(resource)) {
            return null;
        }
        int fixedPartLength = BuildConfig.APP_NAME.length() + 1; // include the trailing dot
        int randomPartLength = 4; // 3 bytes
        if (resource.length() > fixedPartLength + randomPartLength) {
            if (validBase64(
                    resource.substring(fixedPartLength, fixedPartLength + randomPartLength))) {
                return resource.substring(0, fixedPartLength + randomPartLength);
            }
        }
        return resource;
    }

    private static boolean validBase64(String input) {
        try {
            return Base64.decode(input, Base64.URL_SAFE).length == 3;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void changeStatus(final ConnectionState nextStatus) {
        synchronized (this) {
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.debug(
                        account.address
                                + ": not changing status to "
                                + nextStatus
                                + " because thread was interrupted");
                return;
            }
            final ConnectionState current = this.connectionState;
            if (current == nextStatus) {
                return;
            }
            if ((nextStatus == ConnectionState.OFFLINE)
                    && (current != ConnectionState.CONNECTING)
                    && (current != ConnectionState.ONLINE)) {
                return;
            }
            if (nextStatus == ConnectionState.ONLINE) {
                this.attempt = 0;
            }
            this.connectionState = nextStatus;
            if (nextStatus.isError() || nextStatus == ConnectionState.ONLINE) {
                this.recentErrorConnectionState = nextStatus;
            }
            if (nextStatus != ConnectionState.CONNECTING && nextStatus != ConnectionState.OFFLINE) {
                final var future = this.connectedFuture.pop();
                if (future != null) {
                    if (nextStatus == ConnectionState.ONLINE) {
                        future.set(this);
                    } else {
                        future.setException(new ConnectionException(nextStatus));
                    }
                }
            }
        }
        if (statusListener != null) {
            statusListener.accept(this);
        }
    }

    public void prepareNewConnection() {
        this.lastConnect = SystemClock.elapsedRealtime();
        this.lastPingSent = SystemClock.elapsedRealtime();
        this.mWaitingForSmCatchup.set(false);
        this.changeStatus(ConnectionState.CONNECTING);
    }

    public boolean isWaitingForSmCatchup() {
        return mWaitingForSmCatchup.get();
    }

    public void incrementSmCatchupMessageCounter() {
        this.mSmCatchupMessageCounter.incrementAndGet();
    }

    protected void connect() {
        final Connection connection =
                ConversationsDatabase.getInstance(context)
                        .accountDao()
                        .getConnectionSettings(account.id);
        LOGGER.debug(account.address + ": connecting");
        this.encryptionEnabled = false;
        this.inSmacksSession = false;
        this.quickStartInProgress = false;
        this.isBound = false;
        this.attempt++;
        this.verifiedHostname =
                null; // will be set if user entered hostname is being used or hostname was verified
        // with dnssec
        try {
            Socket localSocket;
            shouldAuthenticate =
                    ConversationsDatabase.getInstance(context)
                            .accountDao()
                            .loginAndBind(account.id);
            this.changeStatus(ConnectionState.CONNECTING);
            // TODO introduce proxy check
            final boolean useTor = /*fcontext.useTorToConnect() ||*/ account.isOnion();
            if (useTor) {
                final String destination;
                final int port;
                final boolean directTls;
                if (connection == null || account.isOnion()) {
                    destination = account.address.getDomain().toString();
                    port = 5222;
                    directTls = false;
                } else {
                    destination = connection.hostname;
                    this.verifiedHostname = destination;
                    port = connection.port;
                    directTls = connection.directTls;
                }

                LOGGER.debug(
                        account.address
                                + ": connect to "
                                + destination
                                + " via Tor. directTls="
                                + directTls);
                localSocket = SocksSocketFactory.createSocketOverTor(destination, port);

                if (directTls) {
                    localSocket = upgradeSocketToTls(localSocket);
                    this.encryptionEnabled = true;
                }

                try {
                    startXmpp(localSocket);
                } catch (final InterruptedException e) {
                    LOGGER.debug(
                            account.address + ": thread was interrupted before beginning stream");
                    return;
                } catch (final Exception e) {
                    throw new IOException("Could not start stream", e);
                }
            } else {
                final String domain = account.address.getDomain().toString();
                final List<Resolver.Result> results;
                if (connection != null) {
                    results = Resolver.fromHardCoded(connection.hostname, connection.port);
                } else {
                    results = Resolver.resolve(domain);
                }
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.debug(account.address + ": Thread was interrupted");
                    return;
                }
                if (results.size() == 0) {
                    LOGGER.warn("Resolver results were empty");
                    return;
                }
                final Resolver.Result storedBackupResult;
                if (connection != null) {
                    storedBackupResult = null;
                } else {
                    // TODO fix resolver result caching
                    storedBackupResult =
                            null; // context.databaseBackend.findResolverResult(domain);
                    if (storedBackupResult != null && !results.contains(storedBackupResult)) {
                        results.add(storedBackupResult);
                        LOGGER.debug(
                                account.address
                                        + ": loaded backup resolver result from db: "
                                        + storedBackupResult);
                    }
                }
                for (Iterator<Resolver.Result> iterator = results.iterator();
                        iterator.hasNext(); ) {
                    final Resolver.Result result = iterator.next();
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.debug(account.address + ": Thread was interrupted");
                        return;
                    }
                    try {
                        // if tls is true, encryption is implied and must not be started
                        this.encryptionEnabled = result.isDirectTls();
                        verifiedHostname =
                                result.isAuthenticated() ? result.getHostname().toString() : null;
                        LOGGER.debug("verified hostname " + verifiedHostname);
                        final InetSocketAddress addr;
                        if (result.getIp() != null) {
                            addr = new InetSocketAddress(result.getIp(), result.getPort());
                            LOGGER.debug(
                                    account.address
                                            + ": using values from resolver "
                                            + (result.getHostname() == null
                                                    ? ""
                                                    : result.getHostname().toString() + "/")
                                            + result.getIp().getHostAddress()
                                            + ":"
                                            + result.getPort()
                                            + " tls: "
                                            + this.encryptionEnabled);
                        } else {
                            addr =
                                    new InetSocketAddress(
                                            IDN.toASCII(result.getHostname().toString()),
                                            result.getPort());
                            LOGGER.debug(
                                    account.address
                                            + ": using values from resolver "
                                            + result.getHostname().toString()
                                            + ":"
                                            + result.getPort()
                                            + " tls: "
                                            + this.encryptionEnabled);
                        }

                        localSocket = new Socket();
                        localSocket.connect(addr, ConnectionPool.SOCKET_TIMEOUT * 1000);

                        if (this.encryptionEnabled) {
                            localSocket = upgradeSocketToTls(localSocket);
                        }

                        localSocket.setSoTimeout(ConnectionPool.SOCKET_TIMEOUT * 1000);
                        if (startXmpp(localSocket)) {
                            localSocket.setSoTimeout(
                                    0); // reset to 0; once the connection is established we don’t
                            // want this
                            if (connection == null && !result.equals(storedBackupResult)) {
                                // TODO store resolver result
                                // context.databaseBackend.saveResolverResult(domain, result);
                            }
                            break; // successfully connected to server that speaks xmpp
                        } else {
                            Closables.close(localSocket);
                            throw new StateChangingException(ConnectionState.STREAM_OPENING_ERROR);
                        }
                    } catch (final StateChangingException e) {
                        if (!iterator.hasNext()) {
                            throw e;
                        }
                    } catch (InterruptedException e) {
                        LOGGER.debug(
                                account.address
                                        + ": thread was interrupted before beginning stream");
                        return;
                    } catch (final Throwable e) {
                        LOGGER.debug(
                                account.address
                                        + ": "
                                        + e.getMessage()
                                        + "("
                                        + e.getClass().getName()
                                        + ")");
                        if (!iterator.hasNext()) {
                            throw new UnknownHostException();
                        }
                    }
                }
            }
            processStream();
        } catch (final SecurityException e) {
            this.changeStatus(ConnectionState.MISSING_INTERNET_PERMISSION);
        } catch (final StateChangingException e) {
            this.changeStatus(e.state);
        } catch (final UnknownHostException
                | ConnectException
                | SocksSocketFactory.HostNotFoundException e) {
            this.changeStatus(ConnectionState.SERVER_NOT_FOUND);
        } catch (final SocksSocketFactory.SocksProxyNotFoundException e) {
            this.changeStatus(ConnectionState.TOR_NOT_AVAILABLE);
        } catch (final IOException | XmlPullParserException e) {
            LOGGER.debug(account.address + ": " + e.getMessage());
            this.changeStatus(ConnectionState.OFFLINE);
            this.attempt = Math.max(0, this.attempt - 1);
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                forceCloseSocket();
            } else {
                LOGGER.debug(
                        account.address
                                + ": not force closing socket because thread was interrupted");
            }
        }
    }

    /**
     * Starts xmpp protocol, call after connecting to socket
     *
     * @return true if server returns with valid xmpp, false otherwise
     */
    private boolean startXmpp(final Socket socket) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        this.socket = socket;
        tagReader = new XmlReader();
        if (tagWriter != null) {
            tagWriter.forceClose();
        }
        tagWriter = new TagWriter();
        tagWriter.setOutputStream(socket.getOutputStream());
        tagReader.setInputStream(socket.getInputStream());
        tagWriter.beginDocument();
        final boolean quickStart;
        if (socket instanceof SSLSocket) {
            final SSLSocket sslSocket = (SSLSocket) socket;
            logTlsCipher(sslSocket);
            quickStart = establishStream(SSLSockets.version(sslSocket));
        } else {
            quickStart = establishStream(SSLSockets.Version.NONE);
        }
        final Tag tag = tagReader.readTag();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        final boolean success = tag != null && tag.isStart("stream", Namespace.STREAMS);
        if (success && quickStart) {
            this.quickStartInProgress = true;
        }
        return success;
    }

    private SSLSocketFactory getSSLSocketFactory()
            throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sc = SSLSockets.getSSLContext();
        final KeyManager[] keyManager;
        final Credential credential = CredentialStore.getInstance(context).get(account);
        if (Strings.isNullOrEmpty(credential.privateKeyAlias)) {
            keyManager = null;
        } else {
            keyManager = new KeyManager[] {new MyKeyManager(context, credential)};
        }
        final String domain = account.address.getDomain().toString();
        // TODO we used to use two different trust managers; interactive and non interactive (to
        // trigger SSL cert prompts)
        // we need a better solution for this using live data or similar
        sc.init(
                keyManager,
                new X509TrustManager[] {TrustManagers.getTrustManager()},
                Conversations.SECURE_RANDOM);
        return sc.getSocketFactory();
    }

    @Override
    public void run() {
        synchronized (this) {
            this.mThread = Thread.currentThread();
            if (this.mThread.isInterrupted()) {
                LOGGER.debug(account.address + ": aborting connect because thread was interrupted");
                return;
            }
            forceCloseSocket();
        }
        connect();
    }

    private void processStream() throws XmlPullParserException, IOException {
        final CountDownLatch streamCountDownLatch = new CountDownLatch(1);
        this.mStreamCountDownLatch = streamCountDownLatch;
        Tag nextTag = tagReader.readTag();
        while (nextTag != null && !nextTag.isEnd("stream")) {
            if (nextTag.isStart("error")) {
                processStreamError(nextTag);
            } else if (nextTag.isStart("features", Namespace.STREAMS)) {
                processStreamFeatures(nextTag);
            } else if (nextTag.isStart("proceed", Namespace.TLS)) {
                switchOverToTls();
            } else if (nextTag.isStart("success")) {
                final Element success = tagReader.readElement(nextTag);
                if (processSuccess(success)) {
                    break;
                }

            } else if (nextTag.isStart("failure", Namespace.TLS)) {
                throw new StateChangingException(ConnectionState.TLS_ERROR);
            } else if (nextTag.isStart("failure")) {
                final Element failure = tagReader.readElement(nextTag);
                processFailure(failure);
            } else if (nextTag.isStart("continue", Namespace.SASL_2)) {
                // two step sasl2 - we don’t support this yet
                throw new StateChangingException(ConnectionState.INCOMPATIBLE_CLIENT);
            } else if (nextTag.isStart("challenge")) {
                if (isSecure() && this.saslMechanism != null) {
                    final Element challenge = tagReader.readElement(nextTag);
                    processChallenge(challenge);
                } else {
                    LOGGER.debug(
                            account.address + ": received 'challenge on an unsecure connection");
                    throw new StateChangingException(ConnectionState.INCOMPATIBLE_CLIENT);
                }
            } else if (nextTag.isStart("enabled", Namespace.STREAM_MANAGEMENT)) {
                final Element enabled = tagReader.readElement(nextTag);
                processEnabled(enabled);
            } else if (nextTag.isStart("resumed")) {
                final Element resumed = tagReader.readElement(nextTag);
                processResumed(resumed);
            } else if (nextTag.isStart("r")) {
                tagReader.readElement(nextTag);
                if (EXTENDED_SM_LOGGING) {
                    LOGGER.debug(
                            account.address + ": acknowledging stanza #" + this.stanzasReceived);
                }
                final Ack ack = new Ack(this.stanzasReceived);
                tagWriter.writeStanzaAsync(ack);
            } else if (nextTag.isStart("a")) {
                if (mWaitingForSmCatchup.compareAndSet(true, false)) {
                    final int messageCount = mSmCatchupMessageCounter.get();
                    final int pendingIQs = packetCallbacks.size();
                    LOGGER.debug(
                            account.address
                                    + ": SM catchup complete (messages="
                                    + messageCount
                                    + ", pending IQs="
                                    + pendingIQs
                                    + ")");
                    if (messageCount > 0) {
                        // TODO finish notification backlog (ok to pling now)
                        // context.getNotificationService().finishBacklog(true, account);
                    }
                }
                final Element ack = tagReader.readElement(nextTag);
                lastPacketReceived = SystemClock.elapsedRealtime();
                final boolean acknowledgedMessages;
                synchronized (this.mStanzaQueue) {
                    final Optional<Integer> serverSequence = ack.getOptionalIntAttribute("h");
                    if (serverSequence.isPresent()) {
                        acknowledgedMessages = acknowledgeStanzaUpTo(serverSequence.get());
                    } else {
                        acknowledgedMessages = false;
                        LOGGER.debug(account.address + ": server send ack without sequence number");
                    }
                }
            } else if (nextTag.isStart("failed")) {
                final Element failed = tagReader.readElement(nextTag);
                processFailed(failed, true);
            } else if (nextTag.isStart("iq")) {
                processIq(nextTag);
            } else if (nextTag.isStart("message")) {
                processMessage(nextTag);
            } else if (nextTag.isStart("presence")) {
                processPresence(nextTag);
            }
            nextTag = tagReader.readTag();
        }
        if (nextTag != null && nextTag.isEnd("stream")) {
            streamCountDownLatch.countDown();
        }
    }

    private void processChallenge(final Element challenge) throws IOException {
        final SaslMechanism.Version version;
        try {
            version = SaslMechanism.Version.of(challenge);
        } catch (final IllegalArgumentException e) {
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        final Element response;
        if (version == SaslMechanism.Version.SASL) {
            response = new Element("response", Namespace.SASL);
        } else if (version == SaslMechanism.Version.SASL_2) {
            response = new Element("response", Namespace.SASL_2);
        } else {
            throw new AssertionError("Missing implementation for " + version);
        }
        try {
            response.setContent(
                    saslMechanism.getResponse(challenge.getContent(), sslSocketOrNull(socket)));
        } catch (final SaslMechanism.AuthenticationException e) {
            // TODO: Send auth abort tag.
            LOGGER.error("Authentication failed", e);
            throw new StateChangingException(ConnectionState.UNAUTHORIZED);
        }
        tagWriter.writeElement(response);
    }

    private boolean processSuccess(final Element success)
            throws IOException, XmlPullParserException {
        final SaslMechanism.Version version;
        try {
            version = SaslMechanism.Version.of(success);
        } catch (final IllegalArgumentException e) {
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        final SaslMechanism currentSaslMechanism = this.saslMechanism;
        if (currentSaslMechanism == null) {
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        final String challenge;
        if (version == SaslMechanism.Version.SASL) {
            challenge = success.getContent();
        } else if (version == SaslMechanism.Version.SASL_2) {
            challenge = success.findChildContent("additional-data");
        } else {
            throw new AssertionError("Missing implementation for " + version);
        }
        try {
            currentSaslMechanism.getResponse(challenge, sslSocketOrNull(socket));
        } catch (final SaslMechanism.AuthenticationException e) {
            LOGGER.error("Authentication failed", e);
            throw new StateChangingException(ConnectionState.UNAUTHORIZED);
        }
        LOGGER.debug(account.address + ": logged in (using " + version + ")");
        if (SaslMechanism.pin(currentSaslMechanism)) {
            try {
                CredentialStore.getInstance(context)
                        .setPinnedMechanism(account, currentSaslMechanism);
            } catch (final Exception e) {
                LOGGER.debug("unable to pin mechanism in credential store", e);
            }
        }
        if (version == SaslMechanism.Version.SASL_2) {
            final String authorizationIdentifier =
                    success.findChildContent("authorization-identifier");
            final Jid authorizationJid;
            try {
                authorizationJid =
                        Strings.isNullOrEmpty(authorizationIdentifier)
                                ? null
                                : JidCreate.from(authorizationIdentifier);
            } catch (final XmppStringprepException e) {
                LOGGER.debug(
                        account.address
                                + ": SASL 2.0 authorization identifier was not a valid jid");
                throw new StateChangingException(ConnectionState.BIND_FAILURE);
            }
            if (authorizationJid == null) {
                throw new StateChangingException(ConnectionState.BIND_FAILURE);
            }
            LOGGER.debug(
                    account.address
                            + ": SASL 2.0 authorization identifier was "
                            + authorizationJid);
            if (!account.address.getDomain().equals(authorizationJid.getDomain())) {
                LOGGER.debug(
                        account.address
                                + ": server tried to re-assign domain to "
                                + authorizationJid.getDomain());
                throw new StateChangingError(ConnectionState.BIND_FAILURE);
            }
            setConnectionAddress(authorizationJid);
            final Element bound = success.findChild("bound", Namespace.BIND2);
            final Element resumed = success.findChild("resumed", Namespace.STREAM_MANAGEMENT);
            final Element failed = success.findChild("failed", Namespace.STREAM_MANAGEMENT);
            final Element tokenWrapper = success.findChild("token", Namespace.FAST);
            final String token = tokenWrapper == null ? null : tokenWrapper.getAttribute("token");
            if (bound != null && resumed != null) {
                LOGGER.debug(account.address + ": server sent bound and resumed in SASL2 success");
                throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
            }
            final boolean processNopStreamFeatures;
            if (resumed != null && streamId != null) {
                processResumed(resumed);
            } else if (failed != null) {
                processFailed(failed, false); // wait for new stream features
            }
            if (bound != null) {
                clearIqCallbacks();
                this.isBound = true;
                final Element streamManagementEnabled =
                        bound.findChild("enabled", Namespace.STREAM_MANAGEMENT);
                final Element carbonsEnabled = bound.findChild("enabled", Namespace.CARBONS);
                if (streamManagementEnabled != null) {
                    resetOutboundStanzaQueue();
                    processEnabled(streamManagementEnabled);
                } else {
                    // if we did not enable stream management in bind do it now
                    enableStreamManagement();
                }
                if (carbonsEnabled != null) {
                    LOGGER.debug(account.address + ": successfully enabled carbons");
                }
                sendPostBindInitialization(carbonsEnabled != null);
                processNopStreamFeatures = true;
            } else {
                processNopStreamFeatures = false;
            }
            final HashedToken.Mechanism tokenMechanism;
            if (SaslMechanism.hashedToken(currentSaslMechanism)) {
                tokenMechanism = ((HashedToken) currentSaslMechanism).getTokenMechanism();
            } else if (this.hashTokenRequest != null) {
                tokenMechanism = this.hashTokenRequest;
            } else {
                tokenMechanism = null;
            }
            if (tokenMechanism != null && !Strings.isNullOrEmpty(token)) {
                try {
                    CredentialStore.getInstance(context)
                            .setFastToken(account, tokenMechanism, token);
                    LOGGER.debug(account.address + ": storing hashed token " + tokenMechanism);
                } catch (final Exception e) {
                    LOGGER.debug("could not store fast token", e);
                }
            } else if (this.hashTokenRequest != null) {
                LOGGER.warn(
                        account.address
                                + ": no response to our hashed token request "
                                + this.hashTokenRequest);
            }
            // a successful resume will not send stream features
            if (processNopStreamFeatures) {
                processNopStreamFeatures();
            }
        }
        this.quickStartInProgress = false;
        if (version == SaslMechanism.Version.SASL) {
            tagReader.reset();
            sendStartStream(false, true);
            final Tag tag = tagReader.readTag();
            if (tag != null && tag.isStart("stream", Namespace.STREAMS)) {
                processStream();
                return true;
            } else {
                throw new StateChangingException(ConnectionState.STREAM_OPENING_ERROR);
            }
        } else {
            return false;
        }
    }

    private void resetOutboundStanzaQueue() {
        synchronized (this.mStanzaQueue) {
            final List<Stanza> intermediateStanzas = new ArrayList<>();
            if (EXTENDED_SM_LOGGING) {
                LOGGER.debug(
                        account.address
                                + ": stanzas sent before auth: "
                                + this.stanzasSentBeforeAuthentication);
            }
            for (int i = this.stanzasSentBeforeAuthentication + 1; i <= this.stanzasSent; ++i) {
                final Stanza stanza = this.mStanzaQueue.get(i);
                if (stanza != null) {
                    intermediateStanzas.add(stanza);
                }
            }
            this.mStanzaQueue.clear();
            for (int i = 0; i < intermediateStanzas.size(); ++i) {
                this.mStanzaQueue.put(i, intermediateStanzas.get(i));
            }
            this.stanzasSent = intermediateStanzas.size();
            if (EXTENDED_SM_LOGGING) {
                LOGGER.debug(
                        account.address
                                + ": resetting outbound stanza queue to "
                                + this.stanzasSent);
            }
        }
    }

    private void processNopStreamFeatures() throws IOException {
        final Tag tag = tagReader.readTag();
        if (tag != null && tag.isStart("features", Namespace.STREAMS)) {
            this.streamFeatures = tagReader.readElement(tag, Features.class);
            LOGGER.info(
                    "Processed NOP stream features after success {}",
                    this.streamFeatures.getExtensionIds());
        } else {
            LOGGER.debug(account.address + ": received " + tag);
            LOGGER.debug(
                    account.address + ": server did not send stream features after SASL2 success");
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
    }

    private void processFailure(final Element failure) throws IOException {
        final SaslMechanism.Version version;
        try {
            version = SaslMechanism.Version.of(failure);
        } catch (final IllegalArgumentException e) {
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        LOGGER.debug(failure.toString());
        LOGGER.debug(account.address + ": login failure " + version);
        if (SaslMechanism.hashedToken(this.saslMechanism)) {
            LOGGER.debug(account.address + ": resetting token");
            try {
                CredentialStore.getInstance(context).resetFastToken(account);
            } catch (final Exception e) {
                LOGGER.debug("could not reset fast token in credential store", e);
            }
        }
        if (failure.hasChild("temporary-auth-failure")) {
            throw new StateChangingException(ConnectionState.TEMPORARY_AUTH_FAILURE);
        }
        if (SaslMechanism.hashedToken(this.saslMechanism)) {
            LOGGER.debug(
                    account.address
                            + ": fast authentication failed. falling back to regular"
                            + " authentication");
            authenticate();
        } else {
            throw new StateChangingException(ConnectionState.UNAUTHORIZED);
        }
    }

    private static SSLSocket sslSocketOrNull(final Socket socket) {
        if (socket instanceof SSLSocket) {
            return (SSLSocket) socket;
        } else {
            return null;
        }
    }

    private void processEnabled(final Element enabled) {
        final String streamId;
        if (enabled.getAttributeAsBoolean("resume")) {
            streamId = enabled.getAttribute("id");
            LOGGER.debug(account.address + ": stream management enabled (resumable)");
        } else {
            LOGGER.debug(account.address + ": stream management enabled");
            streamId = null;
        }
        this.streamId = streamId;
        this.stanzasReceived = 0;
        this.inSmacksSession = true;
        final Request r = new Request();
        tagWriter.writeStanzaAsync(r);
    }

    private void processResumed(final Element resumed) throws StateChangingException {
        this.inSmacksSession = true;
        this.isBound = true;
        this.tagWriter.writeStanzaAsync(new Request());
        lastPacketReceived = SystemClock.elapsedRealtime();
        final Optional<Integer> h = resumed.getOptionalIntAttribute("h");
        final int serverCount;
        if (h.isPresent()) {
            serverCount = h.get();
        } else {
            resetStreamId();
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        final ArrayList<Stanza> failedStanzas = new ArrayList<>();
        final boolean acknowledgedMessages;
        synchronized (this.mStanzaQueue) {
            if (serverCount < stanzasSent) {
                LOGGER.debug(account.address + ": session resumed with lost packages");
                stanzasSent = serverCount;
            } else {
                LOGGER.debug(account.address + ": session resumed");
            }
            acknowledgedMessages = acknowledgeStanzaUpTo(serverCount);
            for (int i = 0; i < this.mStanzaQueue.size(); ++i) {
                failedStanzas.add(mStanzaQueue.valueAt(i));
            }
            mStanzaQueue.clear();
        }
        LOGGER.debug(account.address + ": resending " + failedStanzas.size() + " stanzas");
        for (final Stanza packet : failedStanzas) {
            if (packet instanceof Message) {
                Message message = (Message) packet;
                // TODO set ack = false in message table
                // context.markMessage(account, message.getTo().asBareJid(), message.getId(),
                // Message.STATUS_UNSEND);
            }
            sendPacket(packet);
        }
        changeStatusToOnline();
    }

    private void changeStatusToOnline() {
        LOGGER.debug(
                account.address
                        + ": online with resource "
                        + connectionAddress.getResourceOrNull());
        changeStatus(ConnectionState.ONLINE);
    }

    private void processFailed(final Element failed, final boolean sendBindRequest) {
        final Optional<Integer> serverCount = failed.getOptionalIntAttribute("h");
        if (serverCount.isPresent()) {
            LOGGER.debug(
                    account.address
                            + ": resumption failed but server acknowledged stanza #"
                            + serverCount.get());
            final boolean acknowledgedMessages;
            synchronized (this.mStanzaQueue) {
                acknowledgedMessages = acknowledgeStanzaUpTo(serverCount.get());
            }
        } else {
            LOGGER.debug(account.address + ": resumption failed");
        }
        resetStreamId();
        if (sendBindRequest) {
            sendBindRequest();
        }
    }

    private boolean acknowledgeStanzaUpTo(final int serverCount) {
        if (serverCount > stanzasSent) {
            LOGGER.error(
                    "server acknowledged more stanzas than we sent. serverCount="
                            + serverCount
                            + ", ourCount="
                            + stanzasSent);
        }
        boolean acknowledgedMessages = false;
        for (int i = 0; i < mStanzaQueue.size(); ++i) {
            if (serverCount >= mStanzaQueue.keyAt(i)) {
                if (EXTENDED_SM_LOGGING) {
                    LOGGER.debug(
                            account.address
                                    + ": server acknowledged stanza #"
                                    + mStanzaQueue.keyAt(i));
                }
                final Stanza stanza = mStanzaQueue.valueAt(i);
                if (stanza instanceof Message) {
                    final Message packet = (Message) stanza;
                    final String id = packet.getId();
                    final Jid to = packet.getTo();
                    if (id != null && to != null) {
                        acknowledgedMessages |= messageAcknowledgeProcessor.apply(to, id);
                    }
                }
                mStanzaQueue.removeAt(i);
                i--;
            }
        }
        return acknowledgedMessages;
    }

    private <S extends Stanza> S processStanza(final Tag currentTag, final Class<S> clazz)
            throws IOException {
        final S stanza = tagReader.readElement(currentTag, clazz);
        if (stanzasReceived == Integer.MAX_VALUE) {
            resetStreamId();
            throw new IOException("time to restart the session. cant handle >2 billion stanzas");
        }
        if (inSmacksSession) {
            ++stanzasReceived;
        } else if (this.streamFeatures.streamManagement()) {
            LOGGER.debug(
                    account.address
                            + ": not counting stanza("
                            + stanza.getClass().getSimpleName()
                            + "). Not in smacks session.");
        }
        lastPacketReceived = SystemClock.elapsedRealtime();
        // TODO validate to and from
        return stanza;
    }

    private void processIq(final Tag currentTag) throws IOException {
        final Iq packet = processStanza(currentTag, Iq.class);
        final Consumer<Iq> callback;
        synchronized (this.packetCallbacks) {
            final Pair<Iq, Consumer<Iq>> packetCallbackDuple = packetCallbacks.get(packet.getId());
            if (packetCallbackDuple != null) {
                // Packets to the server should have responses from the server
                if (toServer(packetCallbackDuple.first)) {
                    if (fromServer(packet)) {
                        callback = packetCallbackDuple.second;
                        packetCallbacks.remove(packet.getId());
                    } else {
                        callback = null;
                        LOGGER.warn("Ignoring spoofed iq stanza");
                    }
                } else {
                    if (packet.getFrom() != null
                            && packet.getFrom().equals(packetCallbackDuple.first.getTo())) {
                        callback = packetCallbackDuple.second;
                        packetCallbacks.remove(packet.getId());
                    } else {
                        callback = null;
                        LOGGER.error(account.address + ": ignoring spoofed iq packet");
                    }
                }
            } else if (packet.getType() == Iq.Type.GET || packet.getType() == Iq.Type.SET) {
                callback = this.iqPacketConsumer;
            } else {
                callback = null;
            }
        }
        if (callback != null) {
            try {
                callback.accept(packet);
            } catch (StateChangingError error) {
                throw new StateChangingException(error.state);
            }
        }
    }

    private void processMessage(final Tag currentTag) throws IOException {
        final var message = processStanza(currentTag, Message.class);
        this.messagePacketConsumer.accept(message);
    }

    private void processPresence(final Tag currentTag) throws IOException {
        final var presence = processStanza(currentTag, Presence.class);
        this.presencePacketConsumer.accept(presence);
    }

    private void sendStartTLS() throws IOException {
        final Tag startTLS = Tag.empty("starttls");
        startTLS.setAttribute("xmlns", Namespace.TLS);
        tagWriter.writeTag(startTLS);
    }

    private void switchOverToTls() throws XmlPullParserException, IOException {
        tagReader.readTag();
        final Socket socket = this.socket;
        final SSLSocket sslSocket = upgradeSocketToTls(socket);
        tagReader.setInputStream(sslSocket.getInputStream());
        tagWriter.setOutputStream(sslSocket.getOutputStream());
        LOGGER.info("TLS connection established");
        final boolean quickStart;
        try {
            quickStart = establishStream(SSLSockets.version(sslSocket));
        } catch (final InterruptedException e) {
            return;
        }
        if (quickStart) {
            this.quickStartInProgress = true;
        }
        this.encryptionEnabled = true;
        final Tag tag = tagReader.readTag();
        if (tag != null && tag.isStart("stream", Namespace.STREAMS)) {
            logTlsCipher(sslSocket);
            processStream();
        } else {
            throw new StateChangingException(ConnectionState.STREAM_OPENING_ERROR);
        }
        sslSocket.close();
    }

    private void logTlsCipher(final SSLSocket sslSocket) {
        final var session = sslSocket.getSession();
        LOGGER.info(
                "TLS session protocol {} cipher {}",
                session.getProtocol(),
                session.getCipherSuite());
    }

    private SSLSocket upgradeSocketToTls(final Socket socket) throws IOException {
        final SSLSocketFactory sslSocketFactory;
        try {
            sslSocketFactory = getSSLSocketFactory();
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw new StateChangingException(ConnectionState.TLS_ERROR);
        }
        final InetAddress address = socket.getInetAddress();
        final SSLSocket sslSocket =
                (SSLSocket)
                        sslSocketFactory.createSocket(
                                socket, address.getHostAddress(), socket.getPort(), true);
        SSLSockets.setSecurity(sslSocket);
        SSLSockets.setHostname(sslSocket, IDN.toASCII(account.address.getDomain().toString()));
        SSLSockets.setApplicationProtocol(sslSocket, "xmpp-client");
        final XmppDomainVerifier xmppDomainVerifier = new XmppDomainVerifier();
        try {
            if (!xmppDomainVerifier.verify(
                    account.address.getDomain().toString(),
                    this.verifiedHostname,
                    sslSocket.getSession())) {
                LOGGER.debug(account.address + ": TLS certificate domain verification failed");
                Closables.close(sslSocket);
                throw new StateChangingException(ConnectionState.TLS_ERROR_DOMAIN);
            }
        } catch (final SSLPeerUnverifiedException e) {
            Closables.close(sslSocket);
            throw new StateChangingException(ConnectionState.TLS_ERROR);
        }
        return sslSocket;
    }

    private void processStreamFeatures(final Tag currentTag) throws IOException {
        final boolean loginAndBind =
                ConversationsDatabase.getInstance(context).accountDao().loginAndBind(account.id);
        this.streamFeatures = tagReader.readElement(currentTag, Features.class);
        final boolean needsBinding = !isBound && loginAndBind;
        if (this.quickStartInProgress) {
            if (this.streamFeatures.hasChild("authentication", Namespace.SASL_2)) {
                LOGGER.info(
                        "Quick start in progress. ignoring features: {}",
                        this.streamFeatures.getExtensionIds());
                if (SaslMechanism.hashedToken(this.saslMechanism)) {
                    return;
                }
                if (isFastTokenAvailable(
                        this.streamFeatures.findChild("authentication", Namespace.SASL_2))) {
                    LOGGER.debug(account.address + ": fast token available; resetting quick start");
                    ConversationsDatabase.getInstance(context)
                            .accountDao()
                            .setQuickStartAvailable(account.id, false);
                }
                return;
            }
            LOGGER.debug(
                    account.address + ": server lost support for SASL 2. quick start not possible");
            ConversationsDatabase.getInstance(context)
                    .accountDao()
                    .setQuickStartAvailable(account.id, false);
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        if (this.streamFeatures.hasChild("starttls", Namespace.TLS) && !this.encryptionEnabled) {
            LOGGER.info("Negotiating TLS (STARTTLS)");
            sendStartTLS();
            return;
        } else if (!isSecure()) {
            LOGGER.error("Server does not support STARTTLS");
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }

        if (Boolean.FALSE.equals(loginAndBind)) {
            LOGGER.info("No login and bind required. Connection is considered online");
            this.lastPacketReceived = SystemClock.elapsedRealtime();
            this.changeStatus(ConnectionState.ONLINE);
        } else if (this.streamFeatures.hasChild("authentication", Namespace.SASL_2)
                && shouldAuthenticate) {
            authenticate(SaslMechanism.Version.SASL_2);
        } else if (this.streamFeatures.hasChild("mechanisms", Namespace.SASL)
                && shouldAuthenticate) {
            authenticate(SaslMechanism.Version.SASL);
        } else if (this.streamFeatures.streamManagement() && streamId != null && !inSmacksSession) {
            if (EXTENDED_SM_LOGGING) {
                LOGGER.debug(account.address + ": resuming after stanza #" + stanzasReceived);
            }
            final Resume resume = new Resume(this.streamId, stanzasReceived);
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            this.tagWriter.writeStanzaAsync(resume);
        } else if (needsBinding) {
            if (this.streamFeatures.hasChild("bind", Namespace.BIND)) {
                sendBindRequest();
            } else {
                LOGGER.info(
                        "Could not find bind feature. Found {}",
                        this.streamFeatures.getExtensionIds());
                throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
            }
        } else {
            LOGGER.info("Received NOP stream features: {}", this.streamFeatures.getExtensionIds());
        }
    }

    private void authenticate() throws IOException {
        final boolean isSecure = isSecure();
        if (isSecure && this.streamFeatures.hasChild("authentication", Namespace.SASL_2)) {
            authenticate(SaslMechanism.Version.SASL_2);
        } else if (isSecure && this.streamFeatures.hasChild("mechanisms", Namespace.SASL)) {
            authenticate(SaslMechanism.Version.SASL);
        } else {
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
    }

    private boolean isSecure() {
        return this.encryptionEnabled || account.isOnion();
    }

    private void authenticate(final SaslMechanism.Version version) throws IOException {
        final Element authElement;
        if (version == SaslMechanism.Version.SASL) {
            authElement = this.streamFeatures.findChild("mechanisms", Namespace.SASL);
        } else {
            authElement = this.streamFeatures.findChild("authentication", Namespace.SASL_2);
        }
        final Collection<String> mechanisms = SaslMechanism.mechanisms(authElement);
        final Element cbElement =
                this.streamFeatures.findChild("sasl-channel-binding", Namespace.CHANNEL_BINDING);
        final Collection<ChannelBinding> channelBindings = ChannelBinding.of(cbElement);
        final var credential = CredentialStore.getInstance(context).get(account);
        if (credential.isEmpty()) {
            LOGGER.warn("No credentials configured. Going to bail out before actual attempt.");
            throw new StateChangingException(ConnectionState.UNAUTHORIZED);
        }
        final SaslMechanism.Factory saslFactory = new SaslMechanism.Factory(account, credential);
        final SaslMechanism saslMechanism =
                saslFactory.of(
                        mechanisms, channelBindings, version, SSLSockets.version(this.socket));
        this.saslMechanism = validate(saslMechanism, mechanisms);
        final boolean quickStartAvailable;
        final String firstMessage =
                this.saslMechanism.getClientFirstMessage(sslSocketOrNull(this.socket));
        final boolean usingFast = SaslMechanism.hashedToken(this.saslMechanism);
        final Element authenticate;
        if (version == SaslMechanism.Version.SASL) {
            authenticate = new Element("auth", Namespace.SASL);
            if (!Strings.isNullOrEmpty(firstMessage)) {
                authenticate.setContent(firstMessage);
            }
            quickStartAvailable = false;
        } else if (version == SaslMechanism.Version.SASL_2) {
            final Inline inline = authElement.getExtension(Inline.class);
            final boolean sm = inline != null && inline.hasChild("sm", Namespace.STREAM_MANAGEMENT);
            final HashedToken.Mechanism hashTokenRequest;
            if (usingFast) {
                hashTokenRequest = null;
            } else {
                final Element fast =
                        inline == null ? null : inline.findChild("fast", Namespace.FAST);
                final Collection<String> fastMechanisms = SaslMechanism.mechanisms(fast);
                hashTokenRequest =
                        HashedToken.Mechanism.best(fastMechanisms, SSLSockets.version(this.socket));
            }
            final Collection<String> bindFeatures = BindInlineFeatures.get(inline);
            quickStartAvailable =
                    sm
                            && bindFeatures != null
                            && bindFeatures.containsAll(BindInlineFeatures.QUICKSTART_FEATURES);
            this.hashTokenRequest = hashTokenRequest;
            authenticate =
                    generateAuthenticationRequest(
                            firstMessage, usingFast, hashTokenRequest, bindFeatures, sm);
        } else {
            throw new AssertionError("Missing implementation for " + version);
        }

        ConversationsDatabase.getInstance(context)
                .accountDao()
                .setQuickStartAvailable(account.id, quickStartAvailable);

        LOGGER.debug(
                account.address
                        + ": Authenticating with "
                        + version
                        + "/"
                        + this.saslMechanism.getMechanism());
        authenticate.setAttribute("mechanism", this.saslMechanism.getMechanism());
        synchronized (this.mStanzaQueue) {
            this.stanzasSentBeforeAuthentication = this.stanzasSent;
            tagWriter.writeElement(authenticate);
        }
    }

    private static boolean isFastTokenAvailable(final Element authentication) {
        final Element inline = authentication == null ? null : authentication.findChild("inline");
        return inline != null && inline.hasChild("fast", Namespace.FAST);
    }

    @NonNull
    private SaslMechanism validate(
            final @Nullable SaslMechanism saslMechanism, Collection<String> mechanisms)
            throws StateChangingException {
        if (saslMechanism == null) {
            LOGGER.debug(
                    account.address + ": unable to find supported SASL mechanism in " + mechanisms);
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        if (SaslMechanism.hashedToken(saslMechanism)) {
            return saslMechanism;
        }
        final SaslMechanism.Factory saslFactory =
                new SaslMechanism.Factory(
                        account, CredentialStore.getInstance(context).get(account));
        final int pinnedMechanism = saslFactory.getPinnedMechanismPriority();
        if (pinnedMechanism > saslMechanism.getPriority()) {
            LOGGER.error(
                    "Auth failed. Authentication mechanism "
                            + saslMechanism.getMechanism()
                            + " has lower priority ("
                            + saslMechanism.getPriority()
                            + ") than pinned priority ("
                            + pinnedMechanism
                            + "). Possible downgrade attack?");
            throw new StateChangingException(ConnectionState.DOWNGRADE_ATTACK);
        }
        return saslMechanism;
    }

    private Element generateAuthenticationRequest(
            final String firstMessage, final boolean usingFast) {
        return generateAuthenticationRequest(
                firstMessage, usingFast, null, BindInlineFeatures.QUICKSTART_FEATURES, true);
    }

    private Element generateAuthenticationRequest(
            final String firstMessage,
            final boolean usingFast,
            final HashedToken.Mechanism hashedTokenRequest,
            final Collection<String> bind,
            final boolean inlineStreamManagement) {
        final Element authenticate = new Element("authenticate", Namespace.SASL_2);
        if (!Strings.isNullOrEmpty(firstMessage)) {
            authenticate.addChild("initial-response").setContent(firstMessage);
        }
        final Element userAgent = authenticate.addChild("user-agent");
        userAgent.setAttribute("id", account.getPublicDeviceId().toString());
        userAgent.addChild("software").setContent(BuildConfig.APP_NAME);
        userAgent
                .addChild("device")
                .setContent(String.format("%s %s", Build.MANUFACTURER, Build.MODEL));
        if (bind != null) {
            authenticate.addChild(generateBindRequest(bind));
        }
        if (inlineStreamManagement && streamId != null) {
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            authenticate.addExtension(new Resume(this.streamId, this.stanzasReceived));
        }
        if (hashedTokenRequest != null) {
            authenticate
                    .addChild("request-token", Namespace.FAST)
                    .setAttribute("mechanism", hashedTokenRequest.name());
        }
        if (usingFast) {
            authenticate.addChild("fast", Namespace.FAST);
        }
        return authenticate;
    }

    private Element generateBindRequest(final Collection<String> bindFeatures) {
        LOGGER.debug("inline bind features: " + bindFeatures);
        final Element bind = new Element("bind", Namespace.BIND2);
        bind.addChild("tag").setContent(BuildConfig.APP_NAME);
        if (bindFeatures.contains(Namespace.CARBONS)) {
            bind.addChild("enable", Namespace.CARBONS);
        }
        if (bindFeatures.contains(Namespace.STREAM_MANAGEMENT)) {
            bind.addExtension(new Enable());
        }
        return bind;
    }

    private void setAccountCreationFailed(final String url) {
        final HttpUrl httpUrl = url == null ? null : HttpUrl.parse(url);
        if (httpUrl != null && httpUrl.isHttps()) {
            this.redirectionUrl = httpUrl;
            throw new StateChangingError(ConnectionState.REGISTRATION_WEB);
        }
        throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
    }

    public HttpUrl getRedirectionUrl() {
        return this.redirectionUrl;
    }

    public void resetEverything() {
        resetAttemptCount(true);
        resetStreamId();
        clearIqCallbacks();
        this.stanzasSent = 0;
        mStanzaQueue.clear();
        this.redirectionUrl = null;
        this.saslMechanism = null;
    }

    private void sendBindRequest() {
        clearIqCallbacks();
        // TODO if we never store a 'broken' resource we don’t need to fix it
        final String recentResource =
                fixResource(
                        ConversationsDatabase.getInstance(context)
                                .accountDao()
                                .getResource(account.id));
        final String resource;
        if (recentResource != null) {
            resource = recentResource;
        } else {
            resource = this.createNewResource(IDs.tiny(account.randomSeed));
        }
        final Iq iq = new Iq(Iq.Type.SET);
        iq.addChild("bind", Namespace.BIND).addChild("resource").setContent(resource);
        this.sendIqPacketUnbound(
                iq,
                (packet) -> {
                    if (packet.getType() == Iq.Type.TIMEOUT) {
                        return;
                    }
                    final Element bind = packet.findChild("bind");
                    if (bind != null && packet.getType() == Iq.Type.RESULT) {
                        isBound = true;
                        final String jid = bind.findChildContent("jid");
                        if (Strings.isNullOrEmpty(jid)) {
                            throw new StateChangingError(ConnectionState.BIND_FAILURE);
                        }
                        final Jid assignedJid;
                        try {
                            assignedJid = JidCreate.from(jid);
                        } catch (final XmppStringprepException e) {
                            LOGGER.debug(
                                    account.address
                                            + ": server reported invalid jid ("
                                            + jid
                                            + ") on bind");
                            throw new StateChangingError(ConnectionState.BIND_FAILURE);
                        }

                        if (!account.address.getDomain().equals(assignedJid.getDomain())) {
                            LOGGER.debug(
                                    account.address
                                            + ": server tried to re-assign domain to "
                                            + assignedJid.getDomain());
                            throw new StateChangingError(ConnectionState.BIND_FAILURE);
                        }
                        setConnectionAddress(assignedJid);
                        if (streamFeatures.hasChild("session")
                                && !streamFeatures.findChild("session").hasChild("optional")) {
                            sendStartSession();
                        } else {
                            enableStreamManagement();
                            sendPostBindInitialization(false);
                        }
                        return;
                    } else {
                        LOGGER.debug(
                                account.address
                                        + ": disconnecting because of bind failure ("
                                        + packet);
                    }
                    final Element error = packet.findChild("error");
                    if (packet.getType() == Iq.Type.ERROR
                            && error != null
                            && error.hasChild("conflict")) {
                        final String alternativeResource = createNewResource(IDs.tiny());
                        ConversationsDatabase.getInstance(context)
                                .accountDao()
                                .setResource(account.id, alternativeResource);
                        LOGGER.debug(
                                account.address
                                        + ": switching resource due to conflict ("
                                        + alternativeResource
                                        + ")");
                    }
                    throw new StateChangingError(ConnectionState.BIND_FAILURE);
                });
    }

    private void setConnectionAddress(final Jid jid) {
        this.connectionAddress = jid;
    }

    private void clearIqCallbacks() {
        final Iq failurePacket = new Iq(Iq.Type.TIMEOUT);
        final ArrayList<Consumer<Iq>> callbacks = new ArrayList<>();
        synchronized (this.packetCallbacks) {
            if (this.packetCallbacks.size() == 0) {
                return;
            }
            LOGGER.debug(
                    account.address
                            + ": clearing "
                            + this.packetCallbacks.size()
                            + " iq callbacks");
            final Iterator<Pair<Iq, Consumer<Iq>>> iterator =
                    this.packetCallbacks.values().iterator();
            while (iterator.hasNext()) {
                Pair<Iq, Consumer<Iq>> entry = iterator.next();
                callbacks.add(entry.second);
                iterator.remove();
            }
        }
        for (final Consumer<Iq> callback : callbacks) {
            try {
                callback.accept(failurePacket);
            } catch (StateChangingError error) {
                LOGGER.debug(
                        account.address
                                + ": caught StateChangingError("
                                + error.state.toString()
                                + ") while clearing callbacks");
                // ignore
            }
        }
        LOGGER.debug(
                account.address
                        + ": done clearing iq callbacks. "
                        + this.packetCallbacks.size()
                        + " left");
    }

    private void sendStartSession() {
        LOGGER.debug(account.address + ": sending legacy session to outdated server");
        final Iq startSession = new Iq(Iq.Type.SET);
        startSession.addChild("session", "urn:ietf:params:xml:ns:xmpp-session");
        this.sendIqPacketUnbound(
                startSession,
                (packet) -> {
                    if (packet.getType() == Iq.Type.RESULT) {
                        enableStreamManagement();
                        sendPostBindInitialization(false);
                    } else if (packet.getType() != Iq.Type.TIMEOUT) {
                        throw new StateChangingError(ConnectionState.SESSION_FAILURE);
                    }
                });
    }

    // TODO the return value is not used any more
    private boolean enableStreamManagement() {
        final boolean streamManagement = this.streamFeatures.streamManagement();
        if (streamManagement) {
            synchronized (this.mStanzaQueue) {
                final Enable enable = new Enable();
                tagWriter.writeStanzaAsync(enable);
                stanzasSent = 0;
                mStanzaQueue.clear();
            }
            return true;
        } else {
            return false;
        }
    }

    private void sendPostBindInitialization(final boolean carbonsEnabled) {
        getManager(CarbonsManager.class).setEnabled(carbonsEnabled);
        LOGGER.debug(account.address + ": starting service discovery");
        final ArrayList<ListenableFuture<?>> discoFutures = new ArrayList<>();
        final var discoManager = getManager(DiscoManager.class);

        final var nodeHash = this.streamFeatures.getCapabilities();
        final var domainDiscoItem = Entity.discoItem(account.address.asDomainBareJid());
        if (nodeHash != null) {
            discoFutures.add(
                    discoManager.infoOrCache(domainDiscoItem, nodeHash.node, nodeHash.hash));
        } else {
            discoFutures.add(discoManager.info(domainDiscoItem));
        }
        discoFutures.add(discoManager.info(Entity.discoItem(account.address)));
        discoFutures.add(discoManager.itemsWithInfo(domainDiscoItem));

        final var discoFuture =
                Futures.withTimeout(
                        Futures.allAsList(discoFutures),
                        CONNECT_DISCO_TIMEOUT,
                        TimeUnit.SECONDS,
                        ConnectionPool.CONNECTION_SCHEDULER);

        Futures.addCallback(
                discoFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<Object> result) {
                        // TODO enable advanced stream features like carbons
                        finalizeBind();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        LOGGER.debug("unable to fetch disco", t);
                        // TODO reset stream ID so we get a proper connect next time
                        finalizeBind();
                    }
                },
                MoreExecutors.directExecutor());
        this.lastSessionStarted = SystemClock.elapsedRealtime();
    }

    // TODO rename to getConnectionState
    public ConnectionState getStatus() {
        return this.connectionState;
    }

    private void finalizeBind() {
        this.enableAdvancedStreamFeatures();
        this.bindConsumer.accept(this.connectionAddress);
        this.changeStatusToOnline();
    }

    private void enableAdvancedStreamFeatures() {
        if (getManager(CarbonsManager.class).isEnabled()) {
            return;
        }
        if (getManager(DiscoManager.class).hasServerFeature(Namespace.CARBONS)) {
            getManager(CarbonsManager.class).enable();
        }
    }

    private void processStreamError(final Tag currentTag) throws IOException {
        final Element streamError = tagReader.readElement(currentTag);
        if (streamError == null) {
            return;
        }
        if (streamError.hasChild("conflict")) {
            final String alternativeResource = createNewResource(IDs.tiny());
            ConversationsDatabase.getInstance(context)
                    .accountDao()
                    .setResource(account.id, alternativeResource);
            LOGGER.debug(
                    account.address
                            + ": switching resource due to conflict ("
                            + alternativeResource
                            + ")");
            throw new IOException();
        } else if (streamError.hasChild("host-unknown")) {
            throw new StateChangingException(ConnectionState.HOST_UNKNOWN);
        } else if (streamError.hasChild("policy-violation")) {
            this.lastConnect = SystemClock.elapsedRealtime();
            final String text = streamError.findChildContent("text");
            LOGGER.debug(account.address + ": policy violation. " + text);
            failPendingMessages(text);
            throw new StateChangingException(ConnectionState.POLICY_VIOLATION);
        } else {
            LOGGER.debug(account.address + ": stream error " + streamError);
            throw new StateChangingException(ConnectionState.STREAM_ERROR);
        }
    }

    private void failPendingMessages(final String error) {
        synchronized (this.mStanzaQueue) {
            for (int i = 0; i < mStanzaQueue.size(); ++i) {
                final Stanza stanza = mStanzaQueue.valueAt(i);
                if (stanza instanceof Message) {
                    final Message packet = (Message) stanza;
                    final String id = packet.getId();
                    final Jid to = packet.getTo();
                    // TODO set ack=true but add error?
                    // TODO the intent was clearly to stop resending
                    // context.markMessage(account, to.asBareJid(), id, Message.STATUS_SEND_FAILED,
                    // error);
                }
            }
        }
    }

    private boolean establishStream(final SSLSockets.Version sslVersion)
            throws IOException, InterruptedException {
        final SaslMechanism.Factory saslFactory =
                new SaslMechanism.Factory(
                        account, CredentialStore.getInstance(context).get(account));
        final SaslMechanism quickStartMechanism =
                SaslMechanism.ensureAvailable(saslFactory.getQuickStartMechanism(), sslVersion);
        final boolean secureConnection = sslVersion != SSLSockets.Version.NONE;
        if (secureConnection
                && quickStartMechanism != null
                && ConversationsDatabase.getInstance(context)
                        .accountDao()
                        .quickStartAvailable(account.id)) {
            // context.restoredFromDatabaseLatch.await();
            this.saslMechanism = quickStartMechanism;
            final boolean usingFast = quickStartMechanism instanceof HashedToken;
            final Element authenticate =
                    generateAuthenticationRequest(
                            quickStartMechanism.getClientFirstMessage(sslSocketOrNull(this.socket)),
                            usingFast);
            authenticate.setAttribute("mechanism", quickStartMechanism.getMechanism());
            sendStartStream(true, false);
            synchronized (this.mStanzaQueue) {
                this.stanzasSentBeforeAuthentication = this.stanzasSent;
                tagWriter.writeElement(authenticate);
            }
            LOGGER.debug(
                    account.address + ": quick start with " + quickStartMechanism.getMechanism());
            return true;
        } else {
            sendStartStream(secureConnection, true);
            return false;
        }
    }

    private void sendStartStream(final boolean from, final boolean flush) throws IOException {
        final Tag stream = Tag.start("stream:stream");
        stream.setAttribute("to", account.address.asDomainBareJid());
        if (from) {
            stream.setAttribute("from", account.address);
        }
        stream.setAttribute("version", "1.0");
        // TODO use 'en' when privacy mode is enabled
        stream.setAttribute("xml:lang", Locale.getDefault().getLanguage());
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", Namespace.STREAMS);
        tagWriter.writeTag(stream, flush);
    }

    private String createNewResource(final String postfixId) {
        return String.format("%s.%s", BuildConfig.APP_NAME, postfixId);
    }

    public ListenableFuture<Iq> sendIqPacket(final Iq packet) {
        return sendIqPacket(packet, false);
    }

    public ListenableFuture<Iq> sendIqPacketUnbound(final Iq packet) {
        return sendIqPacket(packet, true);
    }

    private ListenableFuture<Iq> sendIqPacket(final Iq packet, final boolean sendToUnboundStream) {
        final SettableFuture<Iq> future = SettableFuture.create();
        sendIqPacket(
                packet,
                result -> {
                    final var type = result.getType();
                    if (type == Iq.Type.RESULT) {
                        future.set(result);
                    } else if (type == Iq.Type.TIMEOUT) {
                        future.setException(new TimeoutException());
                    } else {
                        future.setException(new IqErrorException(result));
                    }
                },
                sendToUnboundStream);
        return future;
    }

    public void sendIqPacket(final Iq packet, final Consumer<Iq> callback) {
        this.sendIqPacket(packet, callback, false);
    }

    public void sendIqPacketUnbound(final Iq packet, final Consumer<Iq> callback) {
        this.sendIqPacket(packet, callback, true);
    }

    private synchronized void sendIqPacket(
            final Iq packet, final Consumer<Iq> callback, final boolean sendToUnboundStream) {
        if (Strings.isNullOrEmpty(packet.getId())) {
            packet.setId(IDs.medium());
        }
        if (callback != null) {
            synchronized (this.packetCallbacks) {
                packetCallbacks.put(packet.getId(), new Pair<>(packet, callback));
            }
        }
        this.sendPacket(packet, sendToUnboundStream);
    }

    public void sendResultFor(final Iq request, final Extension... extensions) {
        final var from = request.getFrom();
        final var id = request.getId();
        final var response = new Iq(Iq.Type.RESULT);
        response.setTo(from);
        response.setId(id);
        for (final Extension extension : extensions) {
            response.addExtension(extension);
        }
        this.sendPacket(response);
    }

    public void sendErrorFor(
            final Iq request,
            final Error.Type type,
            final Condition condition,
            final Error.Extension... extensions) {
        final var from = request.getFrom();
        final var id = request.getId();
        final var response = new Iq(Iq.Type.ERROR);
        response.setTo(from);
        response.setId(id);
        final Error error = response.addExtension(new Error());
        error.setType(type);
        error.setCondition(condition);
        error.addExtensions(extensions);
        this.sendPacket(response);
    }

    public void sendMessagePacket(final Message packet) {
        this.sendPacket(packet);
    }

    public void sendPresencePacket(final Presence packet) {
        this.sendPacket(packet);
    }

    private synchronized void sendPacket(final StreamElement packet) {
        sendPacket(packet, false);
    }

    private synchronized void sendPacket(
            final StreamElement packet, final boolean sendToUnboundStream) {
        if (stanzasSent == Integer.MAX_VALUE) {
            resetStreamId();
            disconnect(true);
            return;
        }
        synchronized (this.mStanzaQueue) {
            if (sendToUnboundStream || isBound) {
                tagWriter.writeStanzaAsync(packet);
            } else {
                LOGGER.debug(
                        account.address
                                + " do not write stanza to unbound stream "
                                + packet.toString());
            }
            if (packet instanceof Stanza) {
                final Stanza stanza = (Stanza) packet;

                if (this.mStanzaQueue.size() != 0) {
                    int currentHighestKey = this.mStanzaQueue.keyAt(this.mStanzaQueue.size() - 1);
                    if (currentHighestKey != stanzasSent) {
                        throw new AssertionError("Stanza count messed up");
                    }
                }

                ++stanzasSent;
                if (EXTENDED_SM_LOGGING) {
                    LOGGER.debug(
                            account.address
                                    + ": counting outbound "
                                    + packet.getName()
                                    + " as #"
                                    + stanzasSent);
                }
                this.mStanzaQueue.append(stanzasSent, stanza);
                if (stanza instanceof Message && stanza.getId() != null && inSmacksSession) {
                    if (EXTENDED_SM_LOGGING) {
                        LOGGER.debug(
                                account.address
                                        + ": requesting ack for message stanza #"
                                        + stanzasSent);
                    }
                    tagWriter.writeStanzaAsync(new Request());
                }
            }
        }
    }

    public void sendPing() {
        if (this.inSmacksSession) {
            this.tagWriter.writeStanzaAsync(new Request());
        } else {
            final Iq iq = new Iq(Iq.Type.GET);
            iq.setFrom(account.address);
            iq.addExtension(new Ping());
            this.sendIqPacket(
                    iq,
                    response -> {
                        LOGGER.info("Server responded to ping");
                    });
        }
        this.lastPingSent = SystemClock.elapsedRealtime();
    }

    public void setOnStatusChangedListener(final Consumer<XmppConnection> listener) {
        this.statusListener = listener;
    }

    public ListenableFuture<XmppConnection> asConnectedFuture() {
        synchronized (this) {
            // TODO some more permanent errors like 'unauthorized' should also return immediate
            if (this.connectionState == ConnectionState.ONLINE) {
                return Futures.immediateFuture(this);
            }
            return this.connectedFuture.peekOrCreate(SettableFuture::create);
        }
    }

    private void forceCloseSocket() {
        Closables.close(this.socket);
        Closables.close(this.tagReader);
    }

    public void interrupt() {
        if (this.mThread != null) {
            this.mThread.interrupt();
        }
    }

    public void disconnect(final boolean force) {
        interrupt();
        LOGGER.debug(account.address + ": disconnecting force=" + force);
        if (force) {
            forceCloseSocket();
        } else {
            final TagWriter currentTagWriter = this.tagWriter;
            if (currentTagWriter.isActive()) {
                currentTagWriter.finish();
                final Socket currentSocket = this.socket;
                final CountDownLatch streamCountDownLatch = this.mStreamCountDownLatch;
                try {
                    currentTagWriter.await(1, TimeUnit.SECONDS);
                    LOGGER.debug(account.address + ": closing stream");
                    currentTagWriter.writeTag(Tag.end("stream:stream"));
                    if (streamCountDownLatch != null) {
                        if (streamCountDownLatch.await(1, TimeUnit.SECONDS)) {
                            LOGGER.debug(account.address + ": remote ended stream");
                        } else {
                            LOGGER.debug(
                                    account.address
                                            + ": remote has not closed socket. force closing");
                        }
                    }
                } catch (InterruptedException e) {
                    LOGGER.debug(account.address + ": interrupted while gracefully closing stream");
                } catch (final IOException e) {
                    LOGGER.debug(
                            account.address
                                    + ": io exception during disconnect ("
                                    + e.getMessage()
                                    + ")");
                } finally {
                    Closables.close(currentSocket);
                }
            } else {
                forceCloseSocket();
            }
        }
    }

    private void resetStreamId() {
        this.streamId = null;
    }

    public int getTimeToNextAttempt() {
        final int additionalTime =
                recentErrorConnectionState == ConnectionState.POLICY_VIOLATION ? 3 : 0;
        final int interval = Math.min((int) (25 * Math.pow(1.3, (additionalTime + attempt))), 300);
        final int secondsSinceLast =
                (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
        return interval - secondsSinceLast;
    }

    public int getAttempt() {
        return this.attempt;
    }

    public long getLastSessionEstablished() {
        final long diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
        return System.currentTimeMillis() - diff;
    }

    public long getLastConnect() {
        return this.lastConnect;
    }

    public long getLastPingSent() {
        return this.lastPingSent;
    }

    public long getLastPacketReceived() {
        return this.lastPacketReceived;
    }

    public void sendActive() {
        this.sendPacket(new Active());
    }

    public void sendInactive() {
        this.sendPacket(new Inactive());
    }

    public void resetAttemptCount(boolean resetConnectTime) {
        this.attempt = 0;
        if (resetConnectTime) {
            this.lastConnect = 0;
        }
    }

    public boolean fromServer(final Stanza stanza) {
        final Jid from = stanza.getFrom();
        return from == null
                || from.equals(connectionAddress.getDomain())
                || from.equals(connectionAddress.asBareJid())
                || from.equals(connectionAddress);
    }

    public boolean toServer(final Stanza stanza) {
        final Jid to = stanza.getTo();
        return to == null
                || to.equals(connectionAddress.getDomain())
                || to.equals(connectionAddress.asBareJid())
                || to.equals(connectionAddress);
    }

    public boolean fromAccount(final Stanza stanza) {
        final Jid from = stanza.getFrom();
        return from == null || from.asBareJid().equals(connectionAddress.asBareJid());
    }

    public boolean toAccount(final Stanza stanza) {
        final Jid to = stanza.getTo();
        return to == null || to.asBareJid().equals(connectionAddress.asBareJid());
    }

    public boolean supportsClientStateIndication() {
        return this.streamFeatures != null && this.streamFeatures.clientStateIndication();
    }

    public Jid getBoundAddress() {
        return this.connectionAddress;
    }

    private static class MyKeyManager implements X509KeyManager {

        private final Context context;
        private final Credential credential;

        private MyKeyManager(Context context, Credential credential) {
            this.context = context;
            this.credential = credential;
        }

        @Override
        public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
            return credential.privateKeyAlias;
        }

        @Override
        public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            LOGGER.debug("getting certificate chain");
            try {
                return KeyChain.getCertificateChain(context, alias);
            } catch (final Exception e) {
                LOGGER.debug("could not get certificate chain", e);
                return new X509Certificate[0];
            }
        }

        @Override
        public String[] getClientAliases(String s, Principal[] principals) {
            final String alias = credential.privateKeyAlias;
            return alias != null ? new String[] {alias} : new String[0];
        }

        @Override
        public String[] getServerAliases(String s, Principal[] principals) {
            return new String[0];
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            try {
                return KeyChain.getPrivateKey(context, alias);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static class StateChangingError extends java.lang.Error {
        private final ConnectionState state;

        public StateChangingError(ConnectionState state) {
            this.state = state;
        }
    }

    private static class StateChangingException extends IOException {
        private final ConnectionState state;

        public StateChangingException(ConnectionState state) {
            this.state = state;
        }
    }

    public abstract static class Delegate {

        protected final Context context;
        protected final XmppConnection connection;

        protected Delegate(final Context context, final XmppConnection connection) {
            this.context = context;
            this.connection = connection;
        }

        protected Account getAccount() {
            return connection.getAccount();
        }

        protected ConversationsDatabase getDatabase() {
            return ConversationsDatabase.getInstance(context);
        }

        protected <T extends AbstractManager> T getManager(Class<T> type) {
            return connection.managers.getInstance(type);
        }
    }
}
