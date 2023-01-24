package im.conversations.android.xmpp;

import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import android.content.Context;
import android.os.SystemClock;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.model.Account;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionPool.class);

    private static volatile ConnectionPool INSTANCE;

    private final Context context;

    private final Executor reconfigurationExecutor = Executors.newSingleThreadExecutor();
    public static final ScheduledExecutorService CONNECTION_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor();

    private final List<XmppConnection> connections = new ArrayList<>();
    private final HashSet<Jid> lowPingTimeoutMode = new HashSet<>();

    private ConnectionPool(final Context context) {
        this.context = context.getApplicationContext();
    }

    public ListenableFuture<Void> reconfigure() {
        final ListenableFuture<List<Account>> accountFuture =
                ConversationsDatabase.getInstance(context).accountDao().getEnabledAccounts();
        return Futures.transform(
                accountFuture,
                accounts -> this.reconfigure(ImmutableSet.copyOf(accounts)),
                reconfigurationExecutor);
    }

    public synchronized XmppConnection reconfigure(final Account account) {
        final Optional<XmppConnection> xmppConnectionOptional =
                Iterables.tryFind(this.connections, c -> c.getAccount().equals(account));
        if (xmppConnectionOptional.isPresent()) {
            return xmppConnectionOptional.get();
        }
        return setupXmppConnection(context, account);
    }

    public synchronized ListenableFuture<XmppConnection> get(final Jid address) {
        final var configured =
                Iterables.tryFind(this.connections, c -> address.equals(c.getAccount().address));
        if (configured.isPresent()) {
            return Futures.immediateFuture(configured.get());
        }
        return Futures.transform(
                ConversationsDatabase.getInstance(context).accountDao().getEnabledAccount(address),
                account -> {
                    if (account == null) {
                        throw new IllegalStateException(
                                String.format(
                                        "No enabled account with address %s",
                                        address.toEscapedString()));
                    }
                    return reconfigure(account);
                },
                reconfigurationExecutor);
    }

    public synchronized ListenableFuture<XmppConnection> get(final long id) {
        final var configured = Iterables.tryFind(this.connections, c -> id == c.getAccount().id);
        if (configured.isPresent()) {
            return Futures.immediateFuture(configured.get());
        }
        return Futures.transform(
                ConversationsDatabase.getInstance(context).accountDao().getEnabledAccount(id),
                account -> {
                    if (account == null) {
                        throw new IllegalStateException(
                                String.format("No enabled account with id %d", id));
                    }
                    return reconfigure(account);
                },
                reconfigurationExecutor);
    }

    private synchronized boolean isEnabled(final long id) {
        return Iterables.any(this.connections, c -> id == c.getAccount().id);
    }

    public synchronized List<XmppConnection> getConnections() {
        return ImmutableList.copyOf(this.connections);
    }

    private synchronized Void reconfigure(final Set<Account> accounts) {
        final Set<Account> current = getAccounts();
        final Set<Account> removed = Sets.difference(current, accounts);
        final Set<Account> added = Sets.difference(accounts, current);
        for (final Account account : added) {
            this.setupXmppConnection(context, account);
        }
        for (final Account account : removed) {
            final Optional<XmppConnection> connectionOptional =
                    Iterables.tryFind(connections, c -> c.getAccount().equals(account));
            if (connectionOptional.isPresent()) {
                final XmppConnection connection = connectionOptional.get();
                disconnect(connection, false);
            }
        }
        return null;
    }

    private void onStatusChanged(final XmppConnection connection) {
        final Account account = connection.getAccount();
        if (connection.getStatus() == ConnectionState.ONLINE || connection.getStatus().isError()) {
            // TODO notify QuickConversationsService of account state change
            // mQuickConversationsService.signalAccountStateChange();
        }

        if (connection.getStatus() == ConnectionState.ONLINE) {
            synchronized (lowPingTimeoutMode) {
                if (lowPingTimeoutMode.remove(account.address)) {
                    LOGGER.debug("{}: leaving low ping timeout mode", account.address);
                }
            }
            ConversationsDatabase.getInstance(context)
                    .accountDao()
                    .setShowErrorNotification(account.id, true);
            if (connection.supportsClientStateIndication()) {
                // TODO send correct CSI state (connection.sendActive or connection.sendInactive)
            }
            scheduleWakeUpCall(Config.PING_MAX_INTERVAL);
        } else if (connection.getStatus() == ConnectionState.OFFLINE) {

            // TODO previously we would call resetSendingToWaiting. The new architecture likely
            // won’t need this but we should double check

            // resetSendingToWaiting(account);
            if (isInLowPingTimeoutMode(account)) {
                LOGGER.debug(
                        "{}: went into offline state during low ping mode. reconnecting now",
                        account.address);
                reconnectAccount(connection);
            } else {
                final int timeToReconnect = SECURE_RANDOM.nextInt(10) + 2;
                scheduleWakeUpCall(timeToReconnect);
            }
        } else if (connection.getStatus() == ConnectionState.REGISTRATION_SUCCESSFUL) {
            // databaseBackend.updateAccount(account);
            reconnectAccount(connection);
        } else if (connection.getStatus() != ConnectionState.CONNECTING) {
            // resetSendingToWaiting(account);
            if (connection.getStatus().isAttemptReconnect()) {
                final int next = connection.getTimeToNextAttempt();
                final boolean lowPingTimeoutMode = isInLowPingTimeoutMode(account);
                if (next <= 0) {
                    LOGGER.debug(
                            "{}: error connecting account. reconnecting now. lowPingTimeout={}",
                            account.address,
                            lowPingTimeoutMode);
                    reconnectAccount(connection);
                } else {
                    final int attempt = connection.getAttempt() + 1;
                    LOGGER.debug(
                            "{}: error connecting account. try again in {}s for the {} time."
                                    + " lowPingTimeout={}",
                            account.address,
                            next,
                            attempt,
                            lowPingTimeoutMode);
                    scheduleWakeUpCall(next);
                }
            }
        }
        // TODO toggle error notification
        // getNotificationService().updateErrorNotification();
    }

    public void scheduleWakeUpCall(final int seconds) {
        CONNECTION_SCHEDULER.schedule(
                () -> {
                    manageConnectionStates();
                },
                Math.max(0, seconds) + 1,
                TimeUnit.SECONDS);
    }

    /** This is called externally if we want to force pings for example on connection switches */
    public void ping() {
        manageConnectionStates(null, true);
    }

    /**
     * This is called externally from the push receiver
     *
     * @param pushedAccountHash
     */
    public void receivePush(final String pushedAccountHash) {
        manageConnectionStates(pushedAccountHash, false);
    }

    private void manageConnectionStates() {
        manageConnectionStates(null, false);
    }

    private void manageConnectionStates(
            final String pushedAccountHash, final boolean immediatePing) {
        // WakeLockHelper.acquire(wakeLock);
        int pingNow = 0;
        final HashSet<XmppConnection> pingCandidates = new HashSet<>();
        final String androidId = PhoneHelper.getAndroidId(context);
        for (final XmppConnection xmppConnection : this.connections) {
            final Account account = xmppConnection.getAccount();
            final boolean pushWasMeantForThisAccount =
                    CryptoHelper.getFingerprint(account.address, androidId)
                            .equals(pushedAccountHash);
            if (processAccountState(xmppConnection, pushWasMeantForThisAccount, pingCandidates)) {
                pingNow++;
            }
        }
        if (pingNow > 0 || immediatePing) {
            for (final XmppConnection xmppConnection : pingCandidates) {
                final Account account = xmppConnection.getAccount();
                final boolean lowTimeout = isInLowPingTimeoutMode(account);
                xmppConnection.sendPing();
                LOGGER.debug("{}: send ping (lowTimeout={})", account.address, lowTimeout);
                scheduleWakeUpCall(lowTimeout ? Config.LOW_PING_TIMEOUT : Config.PING_TIMEOUT);
            }
        }
        // WakeLockHelper.release(wakeLock);
    }

    private boolean processAccountState(
            final XmppConnection connection,
            final boolean isAccountPushed,
            final HashSet<XmppConnection> pingCandidates) {
        boolean pingNow = false;
        if (connection.getStatus().isAttemptReconnect()) {
            final Account account = connection.getAccount();
            if (connection.getStatus() == ConnectionState.ONLINE) {
                synchronized (lowPingTimeoutMode) {
                    final long lastReceived = connection.getLastPacketReceived();
                    final long lastSent = connection.getLastPingSent();
                    final long msToNextPing =
                            (Math.max(lastReceived, lastSent) + Config.PING_MAX_INTERVAL * 1000)
                                    - SystemClock.elapsedRealtime();
                    final int pingTimeout =
                            lowPingTimeoutMode.contains(account.address)
                                    ? Config.LOW_PING_TIMEOUT * 1000
                                    : Config.PING_TIMEOUT * 1000;
                    final long pingTimeoutIn =
                            (lastSent + pingTimeout) - SystemClock.elapsedRealtime();
                    if (lastSent > lastReceived) {
                        if (pingTimeoutIn < 0) {
                            LOGGER.debug("{}: ping timeout", account.address);
                            this.reconnectAccount(connection);
                        } else {
                            this.scheduleWakeUpCall(Ints.saturatedCast(pingTimeoutIn / 1000));
                        }
                    } else {
                        pingCandidates.add(connection);
                        if (isAccountPushed) {
                            pingNow = true;
                            if (lowPingTimeoutMode.add(account.address)) {
                                LOGGER.debug("{}: entering low ping timeout mode", account.address);
                            }
                        } else if (msToNextPing <= 0) {
                            pingNow = true;
                        } else {
                            this.scheduleWakeUpCall(Ints.saturatedCast(msToNextPing / 1000));
                            if (lowPingTimeoutMode.remove(account.address)) {
                                LOGGER.debug("{}: leaving low ping timeout mode", account.address);
                            }
                        }
                    }
                }
            } else if (connection.getStatus() == ConnectionState.OFFLINE) {
                reconnectAccount(connection);
            } else if (connection.getStatus() == ConnectionState.CONNECTING) {
                long secondsSinceLastConnect =
                        (SystemClock.elapsedRealtime() - connection.getLastConnect()) / 1000;
                long timeout = Config.CONNECT_TIMEOUT - secondsSinceLastConnect;
                if (timeout < 0) {
                    LOGGER.debug(
                            "{}: time out during connect reconnecting (secondsSinceLast={})",
                            account.address,
                            secondsSinceLastConnect);
                    connection.resetAttemptCount(false);
                    reconnectAccount(connection);
                }
            } else {
                if (connection.getTimeToNextAttempt() <= 0) {
                    reconnectAccount(connection);
                }
            }
        }
        return pingNow;
    }

    private void reconnectAccount(final XmppConnection connection) {
        final Account account = connection.getAccount();
        if (isEnabled(account.id)) {
            final Thread thread = new Thread(connection);
            connection.prepareNewConnection();
            connection.interrupt();
            thread.start();
            scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT);
        } else {
            disconnect(connection, true);
            connection.resetEverything();
        }
    }

    private void disconnect(final XmppConnection connection, boolean force) {
        if (force) {
            connection.disconnect(true);
        } else {
            // TODO bring back code that gracefully leaves MUCs
            // TODO send offline presence
            connection.disconnect(false);
        }
    }

    private boolean isInLowPingTimeoutMode(Account account) {
        synchronized (lowPingTimeoutMode) {
            return lowPingTimeoutMode.contains(account.address);
        }
    }

    private XmppConnection setupXmppConnection(final Context context, final Account account) {
        final XmppConnection xmppConnection = new XmppConnection(context, account);
        this.connections.add(xmppConnection);
        xmppConnection.setOnStatusChangedListener(this::onStatusChanged);
        reconnectAccount(xmppConnection);
        return xmppConnection;
    }

    private Set<Account> getAccounts() {
        return ImmutableSet.copyOf(Lists.transform(this.connections, XmppConnection::getAccount));
    }

    public static ConnectionPool getInstance(final Context context) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (ConnectionPool.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            INSTANCE = new ConnectionPool(context);
            return INSTANCE;
        }
    }
}
