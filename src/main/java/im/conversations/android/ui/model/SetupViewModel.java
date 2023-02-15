package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import im.conversations.android.ui.Event;
import org.jetbrains.annotations.NotNull;

public class SetupViewModel extends AndroidViewModel {

    private final MutableLiveData<String> xmppAddress = new MutableLiveData<>();
    private final MutableLiveData<String> xmppAddressError = new MutableLiveData<>();
    private final MutableLiveData<String> password = new MutableLiveData<>();
    private final MutableLiveData<String> passwordError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private final MutableLiveData<Event<Target>> redirection = new MutableLiveData<>();

    public SetupViewModel(@NonNull @NotNull Application application) {
        super(application);
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
