package im.conversations.android.transformer;

import android.content.Context;
import im.conversations.android.database.model.Account;

public class Transformer {

    private final Context context;
    private final Account account;

    public Transformer(final Context context, final Account account) {
        this.context = context;
        this.account = account;
    }

    public boolean transform(final Transformation transformation) {
        return true;
    }
}
