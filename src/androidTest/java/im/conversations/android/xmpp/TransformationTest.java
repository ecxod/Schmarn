package im.conversations.android.xmpp;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.transformer.Transformation;
import im.conversations.android.transformer.Transformer;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.reactions.Reaction;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransformationTest {

    private static final Jid ACCOUNT = Jid.of("user@example.com");
    private static final Jid REMOTE = Jid.of("juliet@example.com");

    private static final String GREETING = "Hi Juliet. How are you?";

    private ConversationsDatabase database;
    private Transformer transformer;

    @Before
    public void setupTransformer() throws ExecutionException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        this.database = Room.inMemoryDatabaseBuilder(context, ConversationsDatabase.class).build();
        final var account = new AccountEntity();
        account.address = ACCOUNT;
        account.enabled = true;
        account.randomSeed = IDs.seed();
        final long id = database.accountDao().insert(account);

        this.transformer =
                new Transformer(database, database.accountDao().getEnabledAccount(id).get());
    }

    @Test
    public void reactionBeforeOriginal() {
        final var reactionMessage = new Message();
        reactionMessage.setId("2");
        reactionMessage.setTo(ACCOUNT);
        reactionMessage.setFrom(REMOTE.withResource("junit"));
        final var reactions = reactionMessage.addExtension(new Reactions());
        reactions.setId("1");
        final var reaction = reactions.addExtension(new Reaction());
        reaction.setContent("Y");
        this.transformer.transform(
                Transformation.of(reactionMessage, Instant.now(), REMOTE, "stanza-b", null));
        final var originalMessage = new Message();
        originalMessage.setId("1");
        originalMessage.setTo(REMOTE);
        originalMessage.setFrom(ACCOUNT.withResource("junit"));
        final var body = originalMessage.addExtension(new Body());
        body.setContent(GREETING);
        this.transformer.transform(
                Transformation.of(originalMessage, Instant.now(), REMOTE, "stanza-a", null));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(GREETING, onlyContent.body);
        final var onlyReaction = Iterables.getOnlyElement(message.reactions);
        Assert.assertEquals("Y", onlyReaction.reaction);
        Assert.assertEquals(REMOTE, onlyReaction.reactionBy);
    }
}
