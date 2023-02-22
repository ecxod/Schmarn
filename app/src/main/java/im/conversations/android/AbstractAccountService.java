package im.conversations.android;

import android.content.Context;
import im.conversations.android.database.model.Account;

public abstract class AbstractAccountService {

    protected Context context;
    protected Account account;

    protected AbstractAccountService(final Context context, final Account account) {
        this.context = context;
        this.account = account;
    }
}
