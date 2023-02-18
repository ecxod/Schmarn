package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.R;
import im.conversations.android.database.model.Account;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.ui.Event;
import im.conversations.android.xmpp.ConnectionException;
import im.conversations.android.xmpp.ConnectionState;
import im.conversations.android.xmpp.XmppConnection;
import java.util.Arrays;
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
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final MutableLiveData<Event<Target>> redirection = new MutableLiveData<>();

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

    public LiveData<String> getPasswordError() {
        return Transformations.distinctUntilChanged(this.passwordError);
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
        } catch (final XmppStringprepException e) {
            this.xmppAddressError.postValue(getApplication().getString(R.string.invalid_jid));
            return true;
        }

        if (account != null) {
            if (account.address.equals(address)) {
                this.accountRepository.reconnect(account);
                decideNextStep(Target.ENTER_ADDRESS, account);
                return true;
            } else {
                this.account = null;
                this.accountRepository.deleteAccountAsync(account);
            }
        }
        createAccount(address);
        return true;
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
        this.decideNextStep(Target.ENTER_ADDRESS, account);
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
                        SetupViewModel.this.account = null;
                        redirect(Target.DONE);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        loading.postValue(false);
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
        LOGGER.info("Deciding next step for {}", state);
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
        // TODO show generic error
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
            this.account = null;
            this.accountRepository.deleteAccountAsync(account);
        }
    }

    public LiveData<Event<Target>> getRedirection() {
        return this.redirection;
    }

    public enum Target {
        ENTER_ADDRESS,
        ENTER_PASSWORD,
        ENTER_HOSTNAME,
        DONE
    }
}
