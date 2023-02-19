package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.repository.ChatRepository;
import java.util.List;

public class OverviewViewModel extends AndroidViewModel {

    private final AccountRepository accountRepository;
    private final ChatRepository chatRepository;

    public OverviewViewModel(@NonNull Application application) {
        super(application);
        this.accountRepository = new AccountRepository(application);
        this.chatRepository = new ChatRepository(application);
    }

    public LiveData<List<AccountIdentifier>> getAccounts() {
        return Transformations.distinctUntilChanged(this.accountRepository.getAccounts());
    }

    public LiveData<List<String>> getGroups() {
        return Transformations.distinctUntilChanged(this.chatRepository.getGroups());
    }
}
