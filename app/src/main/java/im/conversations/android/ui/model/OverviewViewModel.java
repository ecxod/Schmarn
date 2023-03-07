package im.conversations.android.ui.model;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.PagingLiveData;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.ChatFilter;
import im.conversations.android.database.model.ChatOverviewItem;
import im.conversations.android.database.model.GroupIdentifier;
import im.conversations.android.repository.AccountRepository;
import im.conversations.android.repository.ChatRepository;
import java.util.List;
import kotlinx.coroutines.CoroutineScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverviewViewModel extends AndroidViewModel {

    private final AccountRepository accountRepository;
    private final ChatRepository chatRepository;
    private final LiveData<List<AccountIdentifier>> accounts;
    private final LiveData<List<GroupIdentifier>> groups;
    private final MediatorLiveData<Boolean> chatFilterAvailable = new MediatorLiveData<>();
    private final MutableLiveData<ChatFilter> chatFilter = new MutableLiveData<>(null);

    private static final Logger LOGGER = LoggerFactory.getLogger(OverviewViewModel.class);

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
        return Transformations.distinctUntilChanged(this.accounts);
    }

    public LiveData<List<GroupIdentifier>> getGroups() {
        return Transformations.distinctUntilChanged(this.groups);
    }

    public LiveData<Boolean> getChatFilterAvailable() {
        return Transformations.distinctUntilChanged(this.chatFilterAvailable);
    }

    public ChatFilter getChatFilter() {
        return this.chatFilter.getValue();
    }

    public void setChatFilter(final ChatFilter chatFilter) {
        this.chatFilter.postValue(chatFilter);
        LOGGER.info("Setting chat filter to {}", chatFilter);
    }

    public LiveData<PagingData<ChatOverviewItem>> getChats() {
        return Transformations.switchMap(
                this.chatFilter,
                input -> {
                    final Pager<Integer, ChatOverviewItem> pager =
                            new Pager<>(
                                    new PagingConfig(20),
                                    () -> {
                                        return chatRepository.getChatOverview(input);
                                    });

                    CoroutineScope viewModelScope = ViewModelKt.getViewModelScope(this);
                    return PagingLiveData.cachedIn(
                            PagingLiveData.getLiveData(pager), viewModelScope);
                });
    }
}
