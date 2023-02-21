package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import im.conversations.android.repository.AccountRepository;

public class MainViewModel extends AndroidViewModel {

    private final AccountRepository accountRepository;
    private final LiveData<Boolean> hasNoAccounts;

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.accountRepository = new AccountRepository(application);
        this.hasNoAccounts = this.accountRepository.hasNoAccounts();
    }

    public LiveData<Boolean> hasNoAccounts() {
        return Transformations.distinctUntilChanged(this.hasNoAccounts);
    }
}
