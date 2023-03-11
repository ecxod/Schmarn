package im.conversations.android.xmpp;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.transformer.Transformer;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;

public abstract class BaseTransformationTest {

    protected static final BareJid ACCOUNT = JidCreate.bareFromOrThrowUnchecked("user@example.com");
    protected static final BareJid REMOTE =
            JidCreate.bareFromOrThrowUnchecked("juliet@example.com");
    protected static final BareJid REMOTE_2 =
            JidCreate.bareFromOrThrowUnchecked("romeo@example.com");

    protected static final String GREETING = "Hi Juliet. How are you?";

    protected ConversationsDatabase database;
    protected Transformer transformer;

    @Before
    public void setupTransformer() throws ExecutionException, InterruptedException {
        final Context context = ApplicationProvider.getApplicationContext();
        this.database = Room.inMemoryDatabaseBuilder(context, ConversationsDatabase.class).build();
        final var account = new AccountEntity();
        account.address = ACCOUNT;
        account.enabled = true;
        account.randomSeed = IDs.seed();
        final long id = database.accountDao().insert(account);

        this.transformer =
                new Transformer(
                        database.accountDao().getEnabledAccount(id).get(), context, database);
    }
}
