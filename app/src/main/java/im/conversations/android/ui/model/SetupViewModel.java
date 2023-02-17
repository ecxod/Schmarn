package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.database.model.Account;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.ui.Event;
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

    public SetupViewModel(@NonNull @NotNull Application application) {
        super(application);
        this.accountRepository = new AccountRepository(application);
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
        redirection.postValue(new Event<>(Target.ENTER_PASSWORD));
        return true;
    }

    public boolean submitPassword() {
        final BareJid address;
        try {
            address = JidCreate.bareFrom(this.xmppAddress.getValue());
        } catch (final XmppStringprepException e) {
            xmppAddressError.postValue("Not a valid jid");
            return true;
        }
        final String password = this.password.getValue();
        final var creationFuture =
                this.accountRepository.createAccountAsync(address, password, true);
        Futures.addCallback(
                creationFuture,
                new FutureCallback<Account>() {
                    @Override
                    public void onSuccess(final Account account) {
                        LOGGER.info("Successfully created account {}", account.address);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable t) {
                        LOGGER.warn("Could not create account", t);
                    }
                },
                MoreExecutors.directExecutor());
        return true;
    }

    public LiveData<Event<Target>> getRedirection() {
        return this.redirection;
    }

    public enum Target {
        ENTER_PASSWORD,
        ENTER_HOSTNAME
    }
}
