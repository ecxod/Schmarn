package im.conversations.android.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.paging.PagingSource;
import im.conversations.android.database.model.ChatFilter;
import im.conversations.android.database.model.ChatOverviewItem;
import im.conversations.android.database.model.GroupIdentifier;
import java.util.List;

public class ChatRepository extends AbstractRepository {

    public ChatRepository(Context context) {
        super(context);
    }

    public LiveData<List<GroupIdentifier>> getGroups() {
        return this.database.chatDao().getGroups();
    }

    public PagingSource<Integer, ChatOverviewItem> getChatOverview(final ChatFilter chatFilter) {
        return this.database.chatDao().getChatOverview(chatFilter);
    }
}
