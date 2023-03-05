package im.conversations.android.ui;

import android.content.Intent;
import com.google.common.base.Strings;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.ChatFilter;
import im.conversations.android.database.model.GroupIdentifier;
import org.jxmpp.jid.impl.JidCreate;

public final class Intents {

    public static final String ACTION_FILTER_CHATS_BY_ACCOUNT = "filter-chats-by-account";
    public static final String ACTION_FILTER_CHATS_BY_GROUP = "filter-chats-by-group";
    public static final String EXTRA_ACCOUNT_ID = "account-id";
    public static final String EXTRA_ACCOUNT_ADDRESS = "account-address";
    public static final String EXTRA_GROUP_ID = "group-id";
    public static final String EXTRA_GROUP_NAME = "group-name";

    private Intents() {}

    public static Intent of(final ChatFilter chatFilter) {
        if (chatFilter instanceof final AccountIdentifier account) {
            final var intent = new Intent(ACTION_FILTER_CHATS_BY_ACCOUNT);
            intent.putExtra(EXTRA_ACCOUNT_ID, account.id);
            intent.putExtra(EXTRA_ACCOUNT_ADDRESS, account.address.toString());
            return intent;
        }
        if (chatFilter instanceof final GroupIdentifier group) {
            final var intent = new Intent(ACTION_FILTER_CHATS_BY_GROUP);
            intent.putExtra(EXTRA_GROUP_ID, group.id);
            intent.putExtra(EXTRA_GROUP_NAME, group.name);
            return intent;
        }
        throw new IllegalStateException(
                String.format("%s is not implemented", chatFilter.getClass().getName()));
    }

    public static ChatFilter toChatFilter(final Intent intent) {
        final var action = intent == null ? null : intent.getAction();
        if (action == null) {
            throw new IllegalArgumentException("Intent doe not specify an action");
        }
        if (ACTION_FILTER_CHATS_BY_ACCOUNT.equals(action)) {
            final var id = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1);
            final var address = intent.getStringExtra(EXTRA_ACCOUNT_ADDRESS);
            if (id < 0 || Strings.isNullOrEmpty(address)) {
                throw new IllegalArgumentException("account filter intent lacks extras");
            }
            return new AccountIdentifier(id, JidCreate.bareFromOrThrowUnchecked(address));
        }
        if (ACTION_FILTER_CHATS_BY_GROUP.equals(action)) {
            final var id = intent.getLongExtra(EXTRA_GROUP_ID, -1);
            final var name = intent.getStringExtra(EXTRA_GROUP_NAME);
            if (id < 0 || Strings.isNullOrEmpty(name)) {
                throw new IllegalArgumentException("group filter intent lack address");
            }
            return new GroupIdentifier(id, name);
        }
        throw new IllegalArgumentException("Unsupported action");
    }
}
