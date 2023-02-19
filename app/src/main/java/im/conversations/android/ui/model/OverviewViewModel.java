package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.ChatFilter;
import im.conversations.android.database.model.GroupIdentifier;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.repository.ChatRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverviewViewModel extends AndroidViewModel {

    private final AccountRepository accountRepository;
    private final ChatRepository chatRepository;
    private final LiveData<List<AccountIdentifier>> accounts;
    private final LiveData<List<GroupIdentifier>> groups;
    private final MediatorLiveData<Boolean> chatFilterAvailable = new MediatorLiveData<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(OverviewViewModel.class);

    private ChatFilter chatFilter;

    public OverviewViewModel(@NonNull Application application) {
        super(application);
        this.accountRepository = new AccountRepository(application);
        this.chatRepository = new ChatRepository(application);
        this.accounts = this.accountRepository.getAccounts();
        this.groups = this.chatRepository.getGroups();
        this.chatFilterAvailable.addSource(
                this.accounts, accounts -> setChatFilterAvailable(accounts, groups.getValue()));
        this.chatFilterAvailable.addSource(
                this.groups, groups -> setChatFilterAvailable(this.accounts.getValue(), groups));
    }

    private void setChatFilterAvailable(
            final List<AccountIdentifier> accounts, final List<GroupIdentifier> groups) {
        this.chatFilterAvailable.setValue(
                (accounts != null && accounts.size() > 1) || (groups != null && groups.size() > 0));
    }

    public LiveData<List<AccountIdentifier>> getAccounts() {
        return Transformations.distinctUntilChanged(this.accountRepository.getAccounts());
    }

    // TODO change into GroupIdentifier
    public LiveData<List<GroupIdentifier>> getGroups() {
        return Transformations.distinctUntilChanged(this.chatRepository.getGroups());
    }

    public LiveData<Boolean> getChatFilterAvailable() {
        return Transformations.distinctUntilChanged(this.chatFilterAvailable);
    }

    public ChatFilter getChatFilter() {
        return this.chatFilter;
    }

    public void setChatFilter(final ChatFilter chatFilter) {
        this.chatFilter = chatFilter;
        LOGGER.info("Setting chat filter to {}", chatFilter);
    }
}
