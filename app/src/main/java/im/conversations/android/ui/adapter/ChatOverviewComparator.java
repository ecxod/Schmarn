package im.conversations.android.ui.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import im.conversations.android.database.model.ChatOverviewItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatOverviewComparator extends DiffUtil.ItemCallback<ChatOverviewItem> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatOverviewComparator.class);

    @Override
    public boolean areItemsTheSame(
            @NonNull ChatOverviewItem oldItem, @NonNull ChatOverviewItem newItem) {
        // LOGGER.info("areItemsTheSame({},{})", oldItem.id, newItem.id);
        return oldItem.id == newItem.id;
    }

    @Override
    public boolean areContentsTheSame(
            @NonNull ChatOverviewItem oldItem, @NonNull ChatOverviewItem newItem) {
        return false;
    }
}
