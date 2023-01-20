package im.conversations.android.repository;

import android.content.Context;
import im.conversations.android.database.ConversationsDatabase;

public abstract class AbstractRepository {

    protected final Context context;
    protected final ConversationsDatabase database;

    protected AbstractRepository(final Context context) {
        this.context = context;
        this.database = ConversationsDatabase.getInstance(context);
    }
}
