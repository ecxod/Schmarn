package im.conversations.android.xmpp;

import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;
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
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.XmppDomainVerifier;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.SSLSockets;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.bind.Bind2;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.EnablePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.ResumePacket;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Connection;
import im.conversations.android.database.model.Credential;
import im.conversations.android.xml.TagWriter;
import im.conversations.android.xmpp.manager.AbstractManager;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.csi.Active;
import im.conversations.android.xmpp.model.csi.Inactive;
import im.conversations.android.xmpp.model.register.Register;
import im.conversations.android.xmpp.model.sm.Ack;
import im.conversations.android.xmpp.model.sm.Enable;
import im.conversations.android.xmpp.model.sm.Request;
import im.conversations.android.xmpp.model.sm.Resume;
import im.conversations.android.xmpp.model.stanza.IQ;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.HttpUrl;
import org.xmlpull.v1.XmlPullParserException;

public class XmppConnection implements Runnable {

    protected final Account account;
    private final SparseArray<Stanza> mStanzaQueue = new SparseArray<>();
    private final Hashtable<String, Pair<IQ, Consumer<IQ>>> packetCallbacks = new Hashtable<>();
    private final Context context;
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
    private final Consumer<IQ> iqPacketConsumer;
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
        int fixedPartLength =
                context.getString(R.string.app_name).length() + 1; // include the trailing dot
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
                Log.d(
                        Config.LOGTAG,
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
        Log.d(Config.LOGTAG, account.address + ": connecting");
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
                    !ConversationsDatabase.getInstance(context)
                            .accountDao()
                            .pendingRegistration(account.id);
            this.changeStatus(ConnectionState.CONNECTING);
            // TODO introduce proxy check
            final boolean useTor = /*fcontext.useTorToConnect() ||*/ account.isOnion();
            if (useTor) {
                final String destination;
                final int port;
                final boolean directTls;
                if (connection == null || account.isOnion()) {
                    destination = account.address.getDomain().toEscapedString();
                    port = 5222;
                    directTls = false;
                } else {
                    destination = connection.hostname;
                    this.verifiedHostname = destination;
                    port = connection.port;
                    directTls = connection.directTls;
                }

                Log.d(
                        Config.LOGTAG,
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
                    Log.d(
                            Config.LOGTAG,
                            account.address + ": thread was interrupted before beginning stream");
                    return;
                } catch (final Exception e) {
                    throw new IOException("Could not start stream", e);
                }
            } else {
                final String domain = account.address.getDomain().toEscapedString();
                final List<Resolver.Result> results;
                if (connection != null) {
                    results = Resolver.fromHardCoded(connection.hostname, connection.port);
                } else {
                    results = Resolver.resolve(domain);
                }
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(Config.LOGTAG, account.address + ": Thread was interrupted");
                    return;
                }
                if (results.size() == 0) {
                    Log.e(Config.LOGTAG, account.address + ": Resolver results were empty");
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
                        Log.d(
                                Config.LOGTAG,
                                account.address
                                        + ": loaded backup resolver result from db: "
                                        + storedBackupResult);
                    }
                }
                for (Iterator<Resolver.Result> iterator = results.iterator();
                        iterator.hasNext(); ) {
                    final Resolver.Result result = iterator.next();
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(Config.LOGTAG, account.address + ": Thread was interrupted");
                        return;
                    }
                    try {
                        // if tls is true, encryption is implied and must not be started
                        this.encryptionEnabled = result.isDirectTls();
                        verifiedHostname =
                                result.isAuthenticated() ? result.getHostname().toString() : null;
                        Log.d(Config.LOGTAG, "verified hostname " + verifiedHostname);
                        final InetSocketAddress addr;
                        if (result.getIp() != null) {
                            addr = new InetSocketAddress(result.getIp(), result.getPort());
                            Log.d(
                                    Config.LOGTAG,
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
                            Log.d(
                                    Config.LOGTAG,
                                    account.address
                                            + ": using values from resolver "
                                            + result.getHostname().toString()
                                            + ":"
                                            + result.getPort()
                                            + " tls: "
                                            + this.encryptionEnabled);
                        }

                        localSocket = new Socket();
                        localSocket.connect(addr, Config.SOCKET_TIMEOUT * 1000);

                        if (this.encryptionEnabled) {
                            localSocket = upgradeSocketToTls(localSocket);
                        }

                        localSocket.setSoTimeout(Config.SOCKET_TIMEOUT * 1000);
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
                            FileBackend.close(localSocket);
                            throw new StateChangingException(ConnectionState.STREAM_OPENING_ERROR);
                        }
                    } catch (final StateChangingException e) {
                        if (!iterator.hasNext()) {
                            throw e;
                        }
                    } catch (InterruptedException e) {
                        Log.d(
                                Config.LOGTAG,
                                account.address
                                        + ": thread was interrupted before beginning stream");
                        return;
                    } catch (final Throwable e) {
                        Log.d(
                                Config.LOGTAG,
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
            Log.d(Config.LOGTAG, account.address + ": " + e.getMessage());
            this.changeStatus(ConnectionState.OFFLINE);
            this.attempt = Math.max(0, this.attempt - 1);
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                forceCloseSocket();
            } else {
                Log.d(
                        Config.LOGTAG,
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
            SSLSockets.log(account.address, sslSocket);
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
        final MemorizingTrustManager trustManager = MemorizingTrustManager.create(context);
        final KeyManager[] keyManager;
        final Credential credential = CredentialStore.getInstance(context).get(account);
        if (Strings.isNullOrEmpty(credential.privateKeyAlias)) {
            keyManager = null;
        } else {
            keyManager = new KeyManager[] {new MyKeyManager(context, credential)};
        }
        final String domain = account.address.getDomain().toEscapedString();
        // TODO we used to use two different trust managers; interactive and non interactive (to
        // trigger SSL cert prompts)
        // we need a better solution for this using live data or similar
        sc.init(
                keyManager,
                new X509TrustManager[] {trustManager.getInteractive(domain)},
                SECURE_RANDOM);
        return sc.getSocketFactory();
    }

    @Override
    public void run() {
        synchronized (this) {
            this.mThread = Thread.currentThread();
            if (this.mThread.isInterrupted()) {
                Log.d(
                        Config.LOGTAG,
                        account.address + ": aborting connect because thread was interrupted");
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
                    Log.d(
                            Config.LOGTAG,
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
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
                            account.address + ": acknowledging stanza #" + this.stanzasReceived);
                }
                final Ack ack = new Ack(this.stanzasReceived);
                tagWriter.writeStanzaAsync(ack);
            } else if (nextTag.isStart("a")) {
                synchronized (NotificationService.CATCHUP_LOCK) {
                    if (mWaitingForSmCatchup.compareAndSet(true, false)) {
                        final int messageCount = mSmCatchupMessageCounter.get();
                        final int pendingIQs = packetCallbacks.size();
                        Log.d(
                                Config.LOGTAG,
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
                        Log.d(
                                Config.LOGTAG,
                                account.address + ": server send ack without sequence number");
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
            Log.e(Config.LOGTAG, e.toString());
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
            Log.e(Config.LOGTAG, String.valueOf(e));
            throw new StateChangingException(ConnectionState.UNAUTHORIZED);
        }
        Log.d(Config.LOGTAG, account.address + ": logged in (using " + version + ")");
        if (SaslMechanism.pin(currentSaslMechanism)) {
            try {
                CredentialStore.getInstance(context)
                        .setPinnedMechanism(account, currentSaslMechanism);
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "unable to pin mechanism in credential store", e);
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
                                : Jid.ofEscaped(authorizationIdentifier);
            } catch (final IllegalArgumentException e) {
                Log.d(
                        Config.LOGTAG,
                        account.address
                                + ": SASL 2.0 authorization identifier was not a valid jid");
                throw new StateChangingException(ConnectionState.BIND_FAILURE);
            }
            if (authorizationJid == null) {
                throw new StateChangingException(ConnectionState.BIND_FAILURE);
            }
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": SASL 2.0 authorization identifier was "
                            + authorizationJid);
            if (!account.address.getDomain().equals(authorizationJid.getDomain())) {
                Log.d(
                        Config.LOGTAG,
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
                Log.d(
                        Config.LOGTAG,
                        account.address + ": server sent bound and resumed in SASL2 success");
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
                    Log.d(Config.LOGTAG, account.address + ": successfully enabled carbons");
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
                    Log.d(
                            Config.LOGTAG,
                            account.address + ": storing hashed token " + tokenMechanism);
                } catch (final Exception e) {
                    Log.d(Config.LOGTAG, "could not store fast token", e);
                }
            } else if (this.hashTokenRequest != null) {
                Log.w(
                        Config.LOGTAG,
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
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
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
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
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
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": processed NOP stream features after success: "
                            + XmlHelper.printElementNames(this.streamFeatures));
        } else {
            Log.d(Config.LOGTAG, account.address + ": received " + tag);
            Log.d(
                    Config.LOGTAG,
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
        Log.d(Config.LOGTAG, failure.toString());
        Log.d(Config.LOGTAG, account.address + ": login failure " + version);
        if (SaslMechanism.hashedToken(this.saslMechanism)) {
            Log.d(Config.LOGTAG, account.address + ": resetting token");
            try {
                CredentialStore.getInstance(context).resetFastToken(account);
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "could not reset fast token in credential store", e);
            }
        }
        if (failure.hasChild("temporary-auth-failure")) {
            throw new StateChangingException(ConnectionState.TEMPORARY_AUTH_FAILURE);
        } else if (failure.hasChild("account-disabled")) {
            final String text = failure.findChildContent("text");
            if (Strings.isNullOrEmpty(text)) {
                throw new StateChangingException(ConnectionState.UNAUTHORIZED);
            }
            final Matcher matcher = Patterns.AUTOLINK_WEB_URL.matcher(text);
            if (matcher.find()) {
                final HttpUrl url;
                try {
                    url = HttpUrl.get(text.substring(matcher.start(), matcher.end()));
                } catch (final IllegalArgumentException e) {
                    throw new StateChangingException(ConnectionState.UNAUTHORIZED);
                }
                if (url.isHttps()) {
                    this.redirectionUrl = url;
                    throw new StateChangingException(ConnectionState.PAYMENT_REQUIRED);
                }
            }
        }
        if (SaslMechanism.hashedToken(this.saslMechanism)) {
            Log.d(
                    Config.LOGTAG,
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
            Log.d(Config.LOGTAG, account.address + ": stream management enabled (resumable)");
        } else {
            Log.d(Config.LOGTAG, account.address + ": stream management enabled");
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
                Log.d(Config.LOGTAG, account.address + ": session resumed with lost packages");
                stanzasSent = serverCount;
            } else {
                Log.d(Config.LOGTAG, account.address + ": session resumed");
            }
            acknowledgedMessages = acknowledgeStanzaUpTo(serverCount);
            for (int i = 0; i < this.mStanzaQueue.size(); ++i) {
                failedStanzas.add(mStanzaQueue.valueAt(i));
            }
            mStanzaQueue.clear();
        }
        Log.d(Config.LOGTAG, account.address + ": resending " + failedStanzas.size() + " stanzas");
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
        Log.d(
                Config.LOGTAG,
                account.address + ": online with resource " + connectionAddress.getResource());
        changeStatus(ConnectionState.ONLINE);
    }

    private void processFailed(final Element failed, final boolean sendBindRequest) {
        final Optional<Integer> serverCount = failed.getOptionalIntAttribute("h");
        if (serverCount.isPresent()) {
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": resumption failed but server acknowledged stanza #"
                            + serverCount.get());
            final boolean acknowledgedMessages;
            synchronized (this.mStanzaQueue) {
                acknowledgedMessages = acknowledgeStanzaUpTo(serverCount.get());
            }
        } else {
            Log.d(Config.LOGTAG, account.address + ": resumption failed");
        }
        resetStreamId();
        if (sendBindRequest) {
            sendBindRequest();
        }
    }

    private boolean acknowledgeStanzaUpTo(final int serverCount) {
        if (serverCount > stanzasSent) {
            Log.e(
                    Config.LOGTAG,
                    "server acknowledged more stanzas than we sent. serverCount="
                            + serverCount
                            + ", ourCount="
                            + stanzasSent);
        }
        boolean acknowledgedMessages = false;
        for (int i = 0; i < mStanzaQueue.size(); ++i) {
            if (serverCount >= mStanzaQueue.keyAt(i)) {
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
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
            throw new IOException("time to restart the session. cant handle >2 billion pcks");
        }
        if (inSmacksSession) {
            ++stanzasReceived;
        } else if (this.streamFeatures.streamManagement()) {
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": not counting stanza("
                            + stanza.getClass().getSimpleName()
                            + "). Not in smacks session.");
        }
        lastPacketReceived = SystemClock.elapsedRealtime();
        if (InvalidJid.invalid(stanza.getTo()) || InvalidJid.invalid(stanza.getFrom())) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid stanza from "
                            + stanza.getFrom()
                            + " to "
                            + stanza.getTo());
        }
        return stanza;
    }

    private void processIq(final Tag currentTag) throws IOException {
        final IQ packet = processStanza(currentTag, IQ.class);
        if (InvalidJid.invalid(packet.getTo()) || InvalidJid.invalid(packet.getFrom())) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid IQ from " + packet.getFrom() + " to " + packet.getTo());
            return;
        }
        final Consumer<IQ> callback;
        synchronized (this.packetCallbacks) {
            final Pair<IQ, Consumer<IQ>> packetCallbackDuple = packetCallbacks.get(packet.getId());
            if (packetCallbackDuple != null) {
                // Packets to the server should have responses from the server
                if (toServer(packetCallbackDuple.first)) {
                    if (fromServer(packet)) {
                        callback = packetCallbackDuple.second;
                        packetCallbacks.remove(packet.getId());
                    } else {
                        callback = null;
                        Log.e(Config.LOGTAG, account.address + ": ignoring spoofed iq packet");
                    }
                } else {
                    if (packet.getFrom() != null
                            && packet.getFrom().equals(packetCallbackDuple.first.getTo())) {
                        callback = packetCallbackDuple.second;
                        packetCallbacks.remove(packet.getId());
                    } else {
                        callback = null;
                        Log.e(Config.LOGTAG, account.address + ": ignoring spoofed iq packet");
                    }
                }
            } else if (packet.getType() == IQ.Type.GET || packet.getType() == IQ.Type.SET) {
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
        if (InvalidJid.invalid(message.getTo()) || InvalidJid.invalid(message.getFrom())) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid Message from "
                            + message.getFrom()
                            + " to "
                            + message.getTo());
            return;
        }
        this.messagePacketConsumer.accept(message);
    }

    private void processPresence(final Tag currentTag) throws IOException {
        final var presence = processStanza(currentTag, Presence.class);
        if (InvalidJid.invalid(presence.getTo()) || InvalidJid.invalid(presence.getFrom())) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid Presence from "
                            + presence.getFrom()
                            + " to "
                            + presence.getTo());
            return;
        }
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
        Log.d(Config.LOGTAG, account.address + ": TLS connection established");
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
            SSLSockets.log(account.address, sslSocket);
            processStream();
        } else {
            throw new StateChangingException(ConnectionState.STREAM_OPENING_ERROR);
        }
        sslSocket.close();
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
        SSLSockets.setHostname(
                sslSocket, IDN.toASCII(account.address.getDomain().toEscapedString()));
        SSLSockets.setApplicationProtocol(sslSocket, "xmpp-client");
        final XmppDomainVerifier xmppDomainVerifier = new XmppDomainVerifier();
        try {
            if (!xmppDomainVerifier.verify(
                    account.address.getDomain().toEscapedString(),
                    this.verifiedHostname,
                    sslSocket.getSession())) {
                Log.d(
                        Config.LOGTAG,
                        account.address + ": TLS certificate domain verification failed");
                FileBackend.close(sslSocket);
                throw new StateChangingException(ConnectionState.TLS_ERROR_DOMAIN);
            }
        } catch (final SSLPeerUnverifiedException e) {
            FileBackend.close(sslSocket);
            throw new StateChangingException(ConnectionState.TLS_ERROR);
        }
        return sslSocket;
    }

    private void processStreamFeatures(final Tag currentTag) throws IOException {
        final boolean pendingRegistration =
                ConversationsDatabase.getInstance(context)
                        .accountDao()
                        .pendingRegistration(account.id);
        this.streamFeatures = tagReader.readElement(currentTag, Features.class);
        final boolean isSecure = isSecure();
        final boolean needsBinding = !isBound && !pendingRegistration;
        if (this.quickStartInProgress) {
            if (this.streamFeatures.hasChild("authentication", Namespace.SASL_2)) {
                Log.d(
                        Config.LOGTAG,
                        account.address
                                + ": quick start in progress. ignoring features: "
                                + XmlHelper.printElementNames(this.streamFeatures));
                if (SaslMechanism.hashedToken(this.saslMechanism)) {
                    return;
                }
                if (isFastTokenAvailable(
                        this.streamFeatures.findChild("authentication", Namespace.SASL_2))) {
                    Log.d(
                            Config.LOGTAG,
                            account.address + ": fast token available; resetting quick start");
                    ConversationsDatabase.getInstance(context)
                            .accountDao()
                            .setQuickStartAvailable(account.id, false);
                }
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    account.address + ": server lost support for SASL 2. quick start not possible");
            ConversationsDatabase.getInstance(context)
                    .accountDao()
                    .setQuickStartAvailable(account.id, false);
            throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
        }
        if (this.streamFeatures.hasChild("starttls", Namespace.TLS) && !this.encryptionEnabled) {
            sendStartTLS();
        } else if (this.streamFeatures.hasChild("register", Namespace.REGISTER_STREAM_FEATURE)
                && pendingRegistration) {
            if (isSecure) {
                register();
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.address
                                + ": unable to find STARTTLS for registration process "
                                + XmlHelper.printElementNames(this.streamFeatures));
                throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
            }
        } else if (!this.streamFeatures.hasChild("register", Namespace.REGISTER_STREAM_FEATURE)
                && pendingRegistration) {
            throw new StateChangingException(ConnectionState.REGISTRATION_NOT_SUPPORTED);
        } else if (this.streamFeatures.hasChild("authentication", Namespace.SASL_2)
                && shouldAuthenticate
                && isSecure) {
            authenticate(SaslMechanism.Version.SASL_2);
        } else if (this.streamFeatures.hasChild("mechanisms", Namespace.SASL)
                && shouldAuthenticate
                && isSecure) {
            authenticate(SaslMechanism.Version.SASL);
        } else if (this.streamFeatures.streamManagement() && streamId != null && !inSmacksSession) {
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
                        account.address + ": resuming after stanza #" + stanzasReceived);
            }
            final Resume resume = new Resume(this.streamId, stanzasReceived);
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            this.tagWriter.writeStanzaAsync(resume);
        } else if (needsBinding) {
            if (this.streamFeatures.hasChild("bind", Namespace.BIND) && isSecure) {
                sendBindRequest();
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.address
                                + ": unable to find bind feature "
                                + XmlHelper.printElementNames(this.streamFeatures));
                throw new StateChangingException(ConnectionState.INCOMPATIBLE_SERVER);
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": received NOP stream features: "
                            + XmlHelper.printElementNames(this.streamFeatures));
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
        return this.encryptionEnabled || Config.ALLOW_NON_TLS_CONNECTIONS || account.isOnion();
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
        final SaslMechanism.Factory saslFactory =
                new SaslMechanism.Factory(
                        account, CredentialStore.getInstance(context).get(account));
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
            final Element inline = authElement.findChild("inline", Namespace.SASL_2);
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
            final Collection<String> bindFeatures = Bind2.features(inline);
            quickStartAvailable =
                    sm
                            && bindFeatures != null
                            && bindFeatures.containsAll(Bind2.QUICKSTART_FEATURES);
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

        Log.d(
                Config.LOGTAG,
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
            Log.d(
                    Config.LOGTAG,
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
            Log.e(
                    Config.LOGTAG,
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
                firstMessage, usingFast, null, Bind2.QUICKSTART_FEATURES, true);
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
        userAgent.addChild("software").setContent(context.getString(R.string.app_name));
        if (!PhoneHelper.isEmulator()) {
            userAgent
                    .addChild("device")
                    .setContent(String.format("%s %s", Build.MANUFACTURER, Build.MODEL));
        }
        if (bind != null) {
            authenticate.addChild(generateBindRequest(bind));
        }
        if (inlineStreamManagement && streamId != null) {
            final ResumePacket resume = new ResumePacket(this.streamId, stanzasReceived);
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            authenticate.addChild(resume);
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
        Log.d(Config.LOGTAG, "inline bind features: " + bindFeatures);
        final Element bind = new Element("bind", Namespace.BIND2);
        bind.addChild("tag").setContent(context.getString(R.string.app_name));
        if (bindFeatures.contains(Namespace.CARBONS)) {
            bind.addChild("enable", Namespace.CARBONS);
        }
        if (bindFeatures.contains(Namespace.STREAM_MANAGEMENT)) {
            bind.addChild(new EnablePacket());
        }
        return bind;
    }

    private void register() {
        final Credential credential = CredentialStore.getInstance(context).get(account);
        final String preAuth = credential.preAuthRegistrationToken;
        if (Strings.isNullOrEmpty(preAuth) || !streamFeatures.invite()) {
            sendRegistryRequest();
            return;
        }
        final IQ preAuthRequest = new IQ(IQ.Type.SET);
        preAuthRequest.addChild("preauth", Namespace.PARS).setAttribute("token", preAuth);
        sendUnmodifiedIqPacket(
                preAuthRequest,
                (response) -> {
                    if (response.getType() == IQ.Type.RESULT) {
                        sendRegistryRequest();
                    } else {
                        final String error = ""; // response.getErrorCondition();
                        Log.d(Config.LOGTAG, account.address + ": failed to pre auth. " + error);
                        throw new StateChangingError(ConnectionState.REGISTRATION_INVALID_TOKEN);
                    }
                },
                true);
    }

    private void sendRegistryRequest() {
        final IQ retrieveRegistration = new IQ(IQ.Type.GET);
        retrieveRegistration.addExtension(new Register());
        retrieveRegistration.setTo(account.address.getDomain());
        sendUnmodifiedIqPacket(
                retrieveRegistration,
                (packet) -> {
                    if (packet.getType() == IQ.Type.TIMEOUT) {
                        return;
                    }
                    if (packet.getType() == IQ.Type.ERROR) {
                        throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
                    }
                    final Register query = packet.getExtension(Register.class);
                    if (query == null) {
                        throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
                    }
                    if (query.hasChild("username") && (query.hasChild("password"))) {
                        final Credential credential =
                                CredentialStore.getInstance(context).get(account);
                        final IQ registrationRequest = new IQ(IQ.Type.SET);
                        final Element username =
                                new Element("username")
                                        .setContent(account.address.getEscapedLocal());
                        final Element password =
                                new Element("password").setContent(credential.password);

                        final var register = registrationRequest.addExtension(new Register());

                        register.addChild(username);
                        register.addChild(password);
                        registrationRequest.setFrom(account.address);
                        sendUnmodifiedIqPacket(
                                registrationRequest, this::handleRegistrationResponse, true);
                    } else if (query.hasChild("x", Namespace.DATA)) {
                        final Data data = Data.parse(query.findChild("x", Namespace.DATA));
                        final Element blob = query.findChild("data", "urn:xmpp:bob");
                        final String id = packet.getId();
                        InputStream is;
                        if (blob != null) {
                            try {
                                final String base64Blob = blob.getContent();
                                final byte[] strBlob = Base64.decode(base64Blob, Base64.DEFAULT);
                                is = new ByteArrayInputStream(strBlob);
                            } catch (Exception e) {
                                is = null;
                            }
                        } else {
                            // TODO this too needs fixing
                            final boolean useTor = /*context.useTorToConnect() ||*/
                                    account.isOnion();
                            try {
                                final String url = data.getValue("url");
                                final String fallbackUrl = data.getValue("captcha-fallback-url");
                                if (url != null) {
                                    is = HttpConnectionManager.open(url, useTor);
                                } else if (fallbackUrl != null) {
                                    is = HttpConnectionManager.open(fallbackUrl, useTor);
                                } else {
                                    is = null;
                                }
                            } catch (final IOException e) {
                                Log.d(
                                        Config.LOGTAG,
                                        account.address + ": unable to fetch captcha",
                                        e);
                                is = null;
                            }
                        }

                        if (is != null) {
                            Bitmap captcha = BitmapFactory.decodeStream(is);
                            throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
                        }
                        throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
                    } else if (query.hasChild("instructions")
                            || query.hasChild("x", Namespace.OOB)) {
                        final String instructions = query.findChildContent("instructions");
                        final Element oob = query.findChild("x", Namespace.OOB);
                        final String url = oob == null ? null : oob.findChildContent("url");
                        if (url != null) {
                            setAccountCreationFailed(url);
                        } else if (instructions != null) {
                            final Matcher matcher = Patterns.AUTOLINK_WEB_URL.matcher(instructions);
                            if (matcher.find()) {
                                setAccountCreationFailed(
                                        instructions.substring(matcher.start(), matcher.end()));
                            }
                        }
                        throw new StateChangingError(ConnectionState.REGISTRATION_FAILED);
                    }
                },
                true);
    }

    private void handleRegistrationResponse(final IQ packet) {
        if (packet.getType() == IQ.Type.RESULT) {
            ConversationsDatabase.getInstance(context)
                    .accountDao()
                    .setPendingRegistration(account.id, false);
            Log.d(
                    Config.LOGTAG,
                    account.address + ": successfully registered new account on server");
            throw new StateChangingError(ConnectionState.REGISTRATION_SUCCESSFUL);
        } else {
            final List<String> PASSWORD_TOO_WEAK_MSGS =
                    Arrays.asList("The password is too weak", "Please use a longer password.");
            Element error = packet.findChild("error");
            ConnectionState state = ConnectionState.REGISTRATION_FAILED;
            if (error != null) {
                if (error.hasChild("conflict")) {
                    state = ConnectionState.REGISTRATION_CONFLICT;
                } else if (error.hasChild("resource-constraint")
                        && "wait".equals(error.getAttribute("type"))) {
                    state = ConnectionState.REGISTRATION_PLEASE_WAIT;
                } else if (error.hasChild("not-acceptable")
                        && PASSWORD_TOO_WEAK_MSGS.contains(error.findChildContent("text"))) {
                    state = ConnectionState.REGISTRATION_PASSWORD_TOO_WEAK;
                }
            }
            throw new StateChangingError(state);
        }
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
        final IQ iq = new IQ(IQ.Type.SET);
        iq.addChild("bind", Namespace.BIND).addChild("resource").setContent(resource);
        this.sendUnmodifiedIqPacket(
                iq,
                (packet) -> {
                    if (packet.getType() == IQ.Type.TIMEOUT) {
                        return;
                    }
                    final Element bind = packet.findChild("bind");
                    if (bind != null && packet.getType() == IQ.Type.RESULT) {
                        isBound = true;
                        final String jid = bind.findChildContent("jid");
                        if (Strings.isNullOrEmpty(jid)) {
                            throw new StateChangingError(ConnectionState.BIND_FAILURE);
                        }
                        final Jid assignedJid;
                        try {
                            assignedJid = Jid.ofEscaped(jid);
                        } catch (final IllegalArgumentException e) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.address
                                            + ": server reported invalid jid ("
                                            + jid
                                            + ") on bind");
                            throw new StateChangingError(ConnectionState.BIND_FAILURE);
                        }

                        if (!account.address.getDomain().equals(assignedJid.getDomain())) {
                            Log.d(
                                    Config.LOGTAG,
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
                        Log.d(
                                Config.LOGTAG,
                                account.address
                                        + ": disconnecting because of bind failure ("
                                        + packet);
                    }
                    final Element error = packet.findChild("error");
                    if (packet.getType() == IQ.Type.ERROR
                            && error != null
                            && error.hasChild("conflict")) {
                        final String alternativeResource = createNewResource(IDs.tiny());
                        ConversationsDatabase.getInstance(context)
                                .accountDao()
                                .setResource(account.id, alternativeResource);
                        Log.d(
                                Config.LOGTAG,
                                account.address
                                        + ": switching resource due to conflict ("
                                        + alternativeResource
                                        + ")");
                    }
                    throw new StateChangingError(ConnectionState.BIND_FAILURE);
                },
                true);
    }

    private void setConnectionAddress(final Jid jid) {
        this.connectionAddress = jid;
    }

    private void clearIqCallbacks() {
        final IQ failurePacket = new IQ(IQ.Type.TIMEOUT);
        final ArrayList<Consumer<IQ>> callbacks = new ArrayList<>();
        synchronized (this.packetCallbacks) {
            if (this.packetCallbacks.size() == 0) {
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    account.address
                            + ": clearing "
                            + this.packetCallbacks.size()
                            + " iq callbacks");
            final Iterator<Pair<IQ, Consumer<IQ>>> iterator =
                    this.packetCallbacks.values().iterator();
            while (iterator.hasNext()) {
                Pair<IQ, Consumer<IQ>> entry = iterator.next();
                callbacks.add(entry.second);
                iterator.remove();
            }
        }
        for (final Consumer<IQ> callback : callbacks) {
            try {
                callback.accept(failurePacket);
            } catch (StateChangingError error) {
                Log.d(
                        Config.LOGTAG,
                        account.address
                                + ": caught StateChangingError("
                                + error.state.toString()
                                + ") while clearing callbacks");
                // ignore
            }
        }
        Log.d(
                Config.LOGTAG,
                account.address
                        + ": done clearing iq callbacks. "
                        + this.packetCallbacks.size()
                        + " left");
    }

    private void sendStartSession() {
        Log.d(Config.LOGTAG, account.address + ": sending legacy session to outdated server");
        final IQ startSession = new IQ(IQ.Type.SET);
        startSession.addChild("session", "urn:ietf:params:xml:ns:xmpp-session");
        this.sendUnmodifiedIqPacket(
                startSession,
                (packet) -> {
                    if (packet.getType() == IQ.Type.RESULT) {
                        enableStreamManagement();
                        sendPostBindInitialization(false);
                    } else if (packet.getType() != IQ.Type.TIMEOUT) {
                        throw new StateChangingError(ConnectionState.SESSION_FAILURE);
                    }
                },
                true);
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
        Log.d(Config.LOGTAG, account.address + ": starting service discovery");
        final ArrayList<ListenableFuture<?>> discoFutures = new ArrayList<>();
        final var discoManager = getManager(DiscoManager.class);

        final var nodeHash = this.streamFeatures.getCapabilities();
        final var domainDiscoItem = Entity.discoItem(account.address.getDomain());
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
                        Config.CONNECT_DISCO_TIMEOUT,
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
                        Log.d(Config.LOGTAG, "unable to fetch disco", t);
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
            Log.d(
                    Config.LOGTAG,
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
            Log.d(Config.LOGTAG, account.address + ": policy violation. " + text);
            failPendingMessages(text);
            throw new StateChangingException(ConnectionState.POLICY_VIOLATION);
        } else {
            Log.d(Config.LOGTAG, account.address + ": stream error " + streamError);
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
                && Config.QUICKSTART_ENABLED
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
            Log.d(
                    Config.LOGTAG,
                    account.address + ": quick start with " + quickStartMechanism.getMechanism());
            return true;
        } else {
            sendStartStream(secureConnection, true);
            return false;
        }
    }

    private void sendStartStream(final boolean from, final boolean flush) throws IOException {
        final Tag stream = Tag.start("stream:stream");
        stream.setAttribute("to", account.address.getDomain());
        if (from) {
            stream.setAttribute("from", account.address);
        }
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", LocalizedContent.STREAM_LANGUAGE);
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", Namespace.STREAMS);
        tagWriter.writeTag(stream, flush);
    }

    private String createNewResource(final String postfixId) {
        return String.format("%s.%s", context.getString(R.string.app_name), postfixId);
    }

    public ListenableFuture<IQ> sendIqPacket(final IQ packet) {
        final SettableFuture<IQ> future = SettableFuture.create();
        sendIqPacket(
                packet,
                result -> {
                    final var type = result.getType();
                    if (type == IQ.Type.RESULT) {
                        future.set(result);
                    } else if (type == IQ.Type.TIMEOUT) {
                        future.setException(new TimeoutException());
                    } else {
                        // TODO some sort of IqErrorException
                        future.setException(new IOException());
                    }
                });
        return future;
    }

    public String sendIqPacket(final IQ packet, final Consumer<IQ> callback) {
        packet.setFrom(account.address);
        return this.sendUnmodifiedIqPacket(packet, callback, false);
    }

    public synchronized String sendUnmodifiedIqPacket(
            final IQ packet, final Consumer<IQ> callback, boolean force) {
        if (Strings.isNullOrEmpty(packet.getId())) {
            packet.setId(IDs.medium());
        }
        if (callback != null) {
            synchronized (this.packetCallbacks) {
                packetCallbacks.put(packet.getId(), new Pair<>(packet, callback));
            }
        }
        this.sendPacket(packet, force);
        return packet.getId();
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

    private synchronized void sendPacket(final StreamElement packet, final boolean force) {
        if (stanzasSent == Integer.MAX_VALUE) {
            resetStreamId();
            disconnect(true);
            return;
        }
        synchronized (this.mStanzaQueue) {
            if (force || isBound) {
                tagWriter.writeStanzaAsync(packet);
            } else {
                Log.d(
                        Config.LOGTAG,
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
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
                            account.address
                                    + ": counting outbound "
                                    + packet.getName()
                                    + " as #"
                                    + stanzasSent);
                }
                this.mStanzaQueue.append(stanzasSent, stanza);
                if (stanza instanceof Message && stanza.getId() != null && inSmacksSession) {
                    if (Config.EXTENDED_SM_LOGGING) {
                        Log.d(
                                Config.LOGTAG,
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
        if (!r()) {
            final IQ iq = new IQ(IQ.Type.GET);
            iq.setFrom(account.address);
            iq.addChild("ping", Namespace.PING);
            this.sendIqPacket(iq, null);
        }
        this.lastPingSent = SystemClock.elapsedRealtime();
    }

    public void setOnStatusChangedListener(final Consumer<XmppConnection> listener) {
        this.statusListener = listener;
    }

    public ListenableFuture<XmppConnection> asConnectedFuture() {
        synchronized (this) {
            if (this.connectionState == ConnectionState.ONLINE) {
                return Futures.immediateFuture(this);
            }
            return this.connectedFuture.peekOrCreate(SettableFuture::create);
        }
    }

    private void forceCloseSocket() {
        FileBackend.close(this.socket);
        FileBackend.close(this.tagReader);
    }

    public void interrupt() {
        if (this.mThread != null) {
            this.mThread.interrupt();
        }
    }

    public void disconnect(final boolean force) {
        interrupt();
        Log.d(Config.LOGTAG, account.address + ": disconnecting force=" + force);
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
                    Log.d(Config.LOGTAG, account.address + ": closing stream");
                    currentTagWriter.writeTag(Tag.end("stream:stream"));
                    if (streamCountDownLatch != null) {
                        if (streamCountDownLatch.await(1, TimeUnit.SECONDS)) {
                            Log.d(Config.LOGTAG, account.address + ": remote ended stream");
                        } else {
                            Log.d(
                                    Config.LOGTAG,
                                    account.address
                                            + ": remote has not closed socket. force closing");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.address + ": interrupted while gracefully closing stream");
                } catch (final IOException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.address
                                    + ": io exception during disconnect ("
                                    + e.getMessage()
                                    + ")");
                } finally {
                    FileBackend.close(currentSocket);
                }
            } else {
                forceCloseSocket();
            }
        }
    }

    private void resetStreamId() {
        this.streamId = null;
    }

    public boolean r() {
        if (this.inSmacksSession) {
            this.tagWriter.writeStanzaAsync(new Request());
            return true;
        } else {
            return false;
        }
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
        // TODO null is valid too?!
        return from != null && from.asBareJid().equals(connectionAddress.asBareJid());
    }

    public boolean toAccount(final Stanza stanza) {
        final Jid to = stanza.getTo();
        return to == null || to.asBareJid().equals(connectionAddress.asBareJid());
    }

    public boolean supportsClientStateIndication() {
        return this.streamFeatures != null && this.streamFeatures.clientStateIndication();
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
            Log.d(Config.LOGTAG, "getting certificate chain");
            try {
                return KeyChain.getCertificateChain(context, alias);
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "could not get certificate chain", e);
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

    private static class StateChangingError extends Error {
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
