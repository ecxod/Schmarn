package im.conversations.android.repository;

import android.content.Context;
import im.conversations.android.database.ConversationsDatabase;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class AbstractRepository {

    protected static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    protected final Context context;
    protected final ConversationsDatabase database;

    protected AbstractRepository(final Context context) {
        this.context = context;
        this.database = ConversationsDatabase.getInstance(context);
    }
}
