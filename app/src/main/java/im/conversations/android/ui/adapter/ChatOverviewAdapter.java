package im.conversations.android.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import im.conversations.android.R;
import im.conversations.android.database.model.ChatOverviewItem;
import im.conversations.android.databinding.ItemChatoverviewBinding;
import im.conversations.android.ui.AvatarFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatOverviewAdapter
        extends PagingDataAdapter<ChatOverviewItem, ChatOverviewAdapter.ChatOverviewViewHolder> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatOverviewAdapter.class);

    public ChatOverviewAdapter(@NonNull DiffUtil.ItemCallback<ChatOverviewItem> diffCallback) {
        super(diffCallback);
    }

    @NonNull
    @Override
    public ChatOverviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ChatOverviewViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.getContext()),
                        R.layout.item_chatoverview,
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChatOverviewViewHolder holder, int position) {
        final var chatOverviewItem = getItem(position);
        holder.binding.setChatOverviewItem(chatOverviewItem);
        final var addressWithName =
                chatOverviewItem == null ? null : chatOverviewItem.getAddressWithName();
        final var avatar = chatOverviewItem == null ? null : chatOverviewItem.getAvatar();
        if (avatar != null) {
            holder.binding.avatar.setVisibility(View.VISIBLE);
            AvatarFetcher.fetchInto(holder.binding.avatar, avatar);
        } else if (addressWithName != null) {
            holder.binding.avatar.setVisibility(View.VISIBLE);
            AvatarFetcher.setDefault(holder.binding.avatar, addressWithName);
        } else {
            holder.binding.avatar.setVisibility(View.INVISIBLE);
        }
    }

    public static class ChatOverviewViewHolder extends RecyclerView.ViewHolder {

        private final ItemChatoverviewBinding binding;

        public ChatOverviewViewHolder(@NonNull ItemChatoverviewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
