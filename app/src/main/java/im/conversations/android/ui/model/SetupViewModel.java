package im.conversations.android.ui.model;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import im.conversations.android.R;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Connection;
import im.conversations.android.dns.Resolver;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.tls.ScopeFingerprint;
import im.conversations.android.ui.Event;
import im.conversations.android.util.ConnectionStates;
import im.conversations.android.xmpp.ConnectionException;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.ConnectionState;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.TrustManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupViewModel extends AndroidViewModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupViewModel.class);

    private final MutableLiveData<String> xmppAddress = new MutableLiveData<>();
    private final MutableLiveData<String> xmppAddressError = new MutableLiveData<>();
    private final MutableLiveData<String> password = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<String> hostname = new MutableLiveData<>();
    private final MutableLiveData<String> hostnameError = new MutableLiveData<>();
    private final MutableLiveData<String> port = new MutableLiveData<>();
    private final MutableLiveData<String> portError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> opportunisticTls = new MutableLiveData<>();

    private final MutableLiveData<Event<String>> genericErrorEvent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final MutableLiveData<Event<Target>> redirection = new MutableLiveData<>();

    private final MutableLiveData<TrustDecision> trustDecision = new MutableLiveData<>();
    private final HashMap<ScopeFingerprint, Boolean> trustDecisions = new HashMap<>();

    private final Function<ScopeFingerprint, ListenableFuture<Boolean>> trustDecisionCallback =
            scopeFingerprint -> {
                final var decision = this.trustDecisions.get(scopeFingerprint);
                if (decision != null) {
                    LOGGER.info("Using previous trust decision ({})", decision);
                    return Futures.immediateFuture(decision);
                }
                LOGGER.info("Trust decision arrived in UI");
                final SettableFuture<Boolean> settableFuture = SettableFuture.create();
                final var trustDecision = new TrustDecision(scopeFingerprint, settableFuture);
                final var currentOperation = this.currentOperation;
                if (currentOperation != null) {
                    currentOperation.cancel(false);
                }
                this.trustDecision.postValue(trustDecision);
                this.redirection.postValue(new Event<>(Target.TRUST_CERTIFICATE));
                return settableFuture;
            };

    private final AccountRepository accountRepository;

    private Account account;
    private ListenableFuture<?> currentOperation;

    public SetupViewModel(@NonNull @NotNull Application application) {
        super(application);
        this.accountRepository = new AccountRepository(application);
        // this clears the error if the user starts typing again
        Transformations.distinctUntilChanged(xmppAddress)
                .observeForever(s -> xmppAddressError.postValue(null));
        Transformations.distinctUntilChanged(password)
                .observeForever(s -> passwordError.postValue(null));
        Transformations.distinctUntilChanged(port).observeForever(s -> portError.postValue(null));
        Transformations.distinctUntilChanged(hostname)
                .observeForever(s -> hostnameError.postValue(null));
    }

    public LiveData<Boolean> isLoading() {
        return this.loading;
    }

    public LiveData<String> getXmppAddressError() {
        return Transformations.distinctUntilChanged(xmppAddressError);
    }

    public MutableLiveData<String> getXmppAddress() {
        return this.xmppAddress;
    }

    public MutableLiveData<String> getPassword() {
        return password;
    }

    public MutableLiveData<String> getHostname() {
        return hostname;
    }

    public LiveData<String> getHostnameError() {
        return this.hostnameError;
    }

    public MutableLiveData<String> getPort() {
        return port;
    }

    public LiveData<String> getPortError() {
        return this.portError;
    }

    public MutableLiveData<Boolean> getOpportunisticTls() {
        return this.opportunisticTls;
    }

    public LiveData<String> getPasswordError() {
        return Transformations.distinctUntilChanged(this.passwordError);
    }

    public LiveData<Event<String>> getGenericErrorEvent() {
        return this.genericErrorEvent;
    }

    public boolean submitXmppAddress() {
        final var account = this.account;
        final var userInput = Strings.nullToEmpty(this.xmppAddress.getValue()).trim();
        if (userInput.isEmpty()) {
            this.xmppAddressError.postValue(
                    getApplication().getString(R.string.please_enter_xmpp_address));
            return true;
        }
        final BareJid address;
        try {
            address = JidCreate.bareFrom(userInput);
            Resolver.checkDomain(address.asDomainBareJid());
        } catch (final XmppStringprepException | IllegalArgumentException e) {
            this.xmppAddressError.postValue(getApplication().getString(R.string.invalid_jid));
            return true;
        }
        this.trustDecisions.clear();
        if (account != null) {
            if (account.address.equals(address)) {
                this.accountRepository.reconnect(account);
                decideNextStep(Target.ENTER_ADDRESS, account);
                return true;
            } else {
                this.unregisterTrustDecisionCallback();
                this.account = null;

                // when the XMPP address changes we want to reset connection info too
                // this is partially to indicate that Conversations might not actually use those
                // connection settings if the connection works without them
                this.hostname.setValue(null);
                this.port.setValue(null);
                this.opportunisticTls.setValue(false);

                this.accountRepository.deleteAccountAsync(account);
            }
        }
        createAccount(address);
        return true;
    }

    public boolean trustCertificate() {
        final var trustDecision = this.trustDecision.getValue();
        final var account = this.account;
        if (trustDecision == null || account == null) {
            // TODO navigate back to sign in or show error?
            return true;
        }
        LOGGER.info("committing trust for {}", trustDecision.scopeFingerprint);
        this.accountRepository.setCertificateTrustedAsync(account, trustDecision.scopeFingerprint);
        // in case the UI interface hook gets called again before this gets written to DB
        this.trustDecisions.put(trustDecision.scopeFingerprint, true);
        if (trustDecision.decision.isDone()) {
            this.accountRepository.reconnect(account);
            LOGGER.info("it was already done. we should reconnect");
        }
        trustDecision.decision.set(true);
        decideNextStep(Target.TRUST_CERTIFICATE, account);
        return true;
    }

    public void rejectTrustDecision() {
        final var trustDecision = this.trustDecision.getValue();
        if (trustDecision == null) {
            return;
        }
        LOGGER.info(
                "Rejecting trust decision for {}",
                TrustManager.fingerprint(trustDecision.scopeFingerprint.fingerprint.array()));
        trustDecision.decision.set(false);
        this.trustDecisions.put(trustDecision.scopeFingerprint, false);
    }

    public LiveData<String> getFingerprint() {
        return Transformations.map(
                this.trustDecision,
                td -> {
                    if (td == null) {
                        return null;
                    } else {
                        return TrustManager.fingerprint(td.scopeFingerprint.fingerprint.array(), 8);
                    }
                });
    }

    private void createAccount(final BareJid address) {

        // if the user hasn't entered anything we want this to be null so we don't store credentials
        final String password = Strings.emptyToNull(this.password.getValue());
        // post parsed/normalized jid back into UI
        this.xmppAddress.postValue(address.toString());
        final var creationFuture = this.accountRepository.createAccountAsync(address, password);
        this.setCurrentOperation(creationFuture);
        Futures.addCallback(
                creationFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Account account) {
                        setAccount(account);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
                        if (throwable instanceof AccountRepository.AccountAlreadyExistsException) {
                            xmppAddressError.postValue(
                                    getApplication().getString(R.string.account_already_setup));
                            return;
                        }
                        LOGGER.warn("Could not create account", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void setCurrentOperation(ListenableFuture<?> currentOperation) {
        this.loading.postValue(true);
        this.currentOperation = currentOperation;
    }

    private void setAccount(@NonNull final Account account) {
        this.account = account;
        this.registerTrustDecisionCallback();
        final var state = this.accountRepository.getConnectionState(account);
        if (Arrays.asList(ConnectionState.TLS_ERROR).contains(state)) {
            LOGGER.info(
                    "Connection had already failed when trust decision callback was registered."
                            + " reconnecting");
            this.accountRepository.reconnect(account);
        }
        this.decideNextStep(Target.ENTER_ADDRESS, account);
    }

    private Optional<TrustManager> getTrustManager() {
        final var account = this.account;
        if (account == null) {
            return Optional.absent();
        }
        return ConnectionPool.getInstance(getApplication())
                .get(account)
                .transform(xc -> xc.getManager(TrustManager.class));
    }

    private void registerTrustDecisionCallback() {
        final var optionalTrustManager = getTrustManager();
        if (optionalTrustManager.isPresent()) {
            optionalTrustManager.get().setUserInterfaceCallback(this.trustDecisionCallback);
            LOGGER.info("Registered user interface callback");
        }
    }

    private void unregisterTrustDecisionCallback() {
        final var optionalTrustManager = getTrustManager();
        if (optionalTrustManager.isPresent()) {
            optionalTrustManager.get().removeUserInterfaceCallback(this.trustDecisionCallback);
        } else {
            LOGGER.warn("No trust manager found");
        }
    }

    private void decideNextStep(final Target current, @NonNull final Account account) {
        final ListenableFuture<XmppConnection> connectedFuture =
                this.accountRepository.getConnectedFuture(account);
        this.setCurrentOperation(connectedFuture);
        Futures.addCallback(
                connectedFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final XmppConnection result) {
                        // TODO only when configured for loginAndBind
                        LOGGER.info("Account setup successful");
                        unregisterTrustDecisionCallback();
                        SetupViewModel.this.account = null;
                        redirect(Target.DONE);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
                        if (throwable instanceof CancellationException) {
                            LOGGER.info("connection future was cancelled");
                            return;
                        }
                        if (throwable instanceof ConnectionException) {
                            decideNextStep(current, ((ConnectionException) throwable));
                        } else {
                            LOGGER.error("Something went wrong bad", throwable);
                            // something went wrong bad. display dialog with error message or
                            // something
                        }
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void decideNextStep(
            final Target current, final ConnectionException connectionException) {
        final var state = connectionException.getConnectionState();
        final var isNetworkDown = isNetworkDown();
        LOGGER.info("Deciding next step for {} isNetworkDown {}", state, isNetworkDown);
        if (state == ConnectionState.SERVER_NOT_FOUND && isNetworkDown) {
            this.genericErrorEvent.postValue(
                    new Event<>(
                            getApplication().getString(R.string.check_your_internet_connection)));
            return;
        }
        if (Arrays.asList(ConnectionState.UNAUTHORIZED, ConnectionState.TEMPORARY_AUTH_FAILURE)
                .contains(state)) {
            if (this.redirectIfNecessary(current, Target.ENTER_PASSWORD)) {
                return;
            }
            passwordError.postValue(
                    getApplication().getString(R.string.account_status_unauthorized));
            return;
        }
        if (Arrays.asList(
                        ConnectionState.HOST_UNKNOWN,
                        ConnectionState.STREAM_OPENING_ERROR,
                        ConnectionState.SERVER_NOT_FOUND)
                .contains(state)) {
            if (this.redirectIfNecessary(current, Target.ENTER_HOSTNAME)) {
                return;
            }
        }
        this.genericErrorEvent.postValue(
                new Event<>(getApplication().getString(ConnectionStates.toStringRes(state))));
    }

    private boolean redirectIfNecessary(final Target current, final Target next) {
        if (current == next) {
            return false;
        }
        return redirect(next);
    }

    private boolean redirect(final Target next) {
        this.redirection.postValue(new Event<>(next));
        return true;
    }

    public boolean submitHostname() {
        final var account = this.account;
        if (account == null) {
            this.redirectIfNecessary(Target.ENTER_HOSTNAME, Target.ENTER_ADDRESS);
            return true;
        }
        final String hostname =
                Strings.nullToEmpty(this.hostname.getValue()).trim().toLowerCase(Locale.ROOT);
        if (hostname.isEmpty()
                || CharMatcher.whitespace().matchesAnyOf(hostname)
                || Resolver.invalidHostname(hostname)) {
            this.hostnameError.postValue(getApplication().getString(R.string.not_valid_hostname));
            return true;
        }
        final Integer port = Ints.tryParse(Strings.nullToEmpty(this.port.getValue()));
        if (port == null || port < 0 || port > 65535) {
            this.portError.postValue(getApplication().getString(R.string.invalid));
            return true;
        }
        this.trustDecisions.clear();
        final boolean directTls = Boolean.FALSE.equals(this.opportunisticTls.getValue());
        final var connection = new Connection(hostname, port, directTls);
        final var setConnectionFuture =
                this.accountRepository.setConnectionAsync(account, connection);
        this.setCurrentOperation(setConnectionFuture);
        Futures.addCallback(
                setConnectionFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Account result) {
                        decideNextStep(Target.ENTER_HOSTNAME, account);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
                        // TODO error message?!
                    }
                },
                MoreExecutors.directExecutor());
        return true;
    }

    public boolean submitPassword() {
        final var account = this.account;
        if (account == null) {
            this.redirectIfNecessary(Target.ENTER_PASSWORD, Target.ENTER_ADDRESS);
            return true;
        }
        final String password = Strings.nullToEmpty(this.password.getValue());
        final var setPasswordFuture = this.accountRepository.setPasswordAsync(account, password);
        this.setCurrentOperation(setPasswordFuture);
        Futures.addCallback(
                setPasswordFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Account account) {
                        decideNextStep(Target.ENTER_PASSWORD, account);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        // TODO show some sort of error message
                        loading.postValue(false);
                    }
                },
                MoreExecutors.directExecutor());
        return true;
    }

    public boolean cancelCurrentOperation() {
        final var currentFuture = this.currentOperation;
        if (currentFuture == null || currentFuture.isDone()) {
            return false;
        }
        return currentFuture.cancel(true);
    }

    public void cancelSetup() {
        final var account = this.account;
        if (account != null) {
            this.unregisterTrustDecisionCallback();
            this.account = null;
            this.accountRepository.deleteAccountAsync(account);
        }
    }

    public LiveData<Event<Target>> getRedirection() {
        return this.redirection;
    }

    @Override
    public void onCleared() {
        LOGGER.info("Clearing view model");
        this.unregisterTrustDecisionCallback();
        super.onCleared();
    }

    private boolean isNetworkDown() {
        final ConnectivityManager cm = getApplication().getSystemService(ConnectivityManager.class);
        final Network activeNetwork = cm == null ? null : cm.getActiveNetwork();
        final NetworkCapabilities capabilities =
                activeNetwork == null ? null : cm.getNetworkCapabilities(activeNetwork);
        return capabilities == null
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public enum Target {
        ENTER_ADDRESS,
        ENTER_PASSWORD,
        ENTER_HOSTNAME,

        TRUST_CERTIFICATE,
        DONE
    }

    public static class TrustDecision {
        public final ScopeFingerprint scopeFingerprint;
        public final SettableFuture<Boolean> decision;

        public TrustDecision(ScopeFingerprint scopeFingerprint, SettableFuture<Boolean> decision) {
            this.scopeFingerprint = scopeFingerprint;
            this.decision = decision;
        }
    }
}
