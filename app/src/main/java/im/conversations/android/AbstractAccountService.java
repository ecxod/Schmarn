package im.conversations.android;

import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.model.Account;

public abstract class AbstractAccountService {

    protected Account account;
    protected ConversationsDatabase database;

    protected AbstractAccountService(final Account account, final ConversationsDatabase database) {
        this.account = account;
        this.database = database;
    }
}
