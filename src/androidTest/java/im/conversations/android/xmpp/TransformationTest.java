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
import im.conversations.android.database.model.Modification;
import im.conversations.android.transformer.Transformation;
import im.conversations.android.transformer.Transformer;
import im.conversations.android.xmpp.model.correction.Replace;
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

    @Test
    public void multipleReactions() {
        final var group = Jid.ofEscaped("a@group.example.com");
        final var message = new Message(Message.Type.GROUPCHAT);
        message.addExtension(new Body("Please give me a thumbs up"));
        message.setFrom(group.withResource("user-a"));
        this.transformer.transform(
                Transformation.of(message, Instant.now(), REMOTE, "stanza-a", "id-user-a"));

        final var reactionA = new Message(Message.Type.GROUPCHAT);
        reactionA.setFrom(group.withResource("user-b"));
        reactionA.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("Y"));
        this.transformer.transform(
                Transformation.of(reactionA, Instant.now(), REMOTE, "stanza-b", "id-user-b"));

        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(group.withResource("user-c"));
        reactionB.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("Y"));
        this.transformer.transform(
                Transformation.of(reactionB, Instant.now(), REMOTE, "stanza-c", "id-user-c"));

        final var reactionC = new Message(Message.Type.GROUPCHAT);
        reactionC.setFrom(group.withResource("user-d"));
        final var reactions = reactionC.addExtension(Reactions.to("stanza-a"));
        reactions.addExtension(new Reaction("Y"));
        reactions.addExtension(new Reaction("Z"));
        this.transformer.transform(
                Transformation.of(reactionC, Instant.now(), REMOTE, "stanza-d", "id-user-d"));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var dbMessage = Iterables.getOnlyElement(messages);
        Assert.assertEquals(4, dbMessage.reactions.size());
        final var aggregated = dbMessage.getAggregatedReactions();
        final var mostFrequentReaction = Iterables.get(aggregated, 0);
        Assert.assertEquals("Y", mostFrequentReaction.getKey());
        Assert.assertEquals(3L, (long) mostFrequentReaction.getValue());
        final var secondReaction = Iterables.get(aggregated, 1);
        Assert.assertEquals("Z", secondReaction.getKey());
        Assert.assertEquals(1L, (long) secondReaction.getValue());
    }

    @Test
    public void correctionBeforeOriginal() {

        final var messageCorrection = new Message();
        messageCorrection.setId("2");
        messageCorrection.setTo(ACCOUNT);
        messageCorrection.setFrom(REMOTE.withResource("junit"));
        messageCorrection.addExtension(new Body()).setContent("Hi example!");
        messageCorrection.addExtension(new Replace()).setId("1");

        this.transformer.transform(
                Transformation.of(messageCorrection, Instant.now(), REMOTE, "stanza-a", null));

        // the correction should not show up as a message
        Assert.assertEquals(0, database.messageDao().getMessages(1L).size());

        final var messageWithTypo = new Message();
        messageWithTypo.setId("1");
        messageWithTypo.setTo(ACCOUNT);
        messageWithTypo.setFrom(REMOTE.withResource("junit"));
        messageWithTypo.addExtension(new Body()).setContent("Hii example!");

        this.transformer.transform(
                Transformation.of(messageWithTypo, Instant.now(), REMOTE, "stanza-b", null));

        final var messages = database.messageDao().getMessages(1L);

        Assert.assertEquals(1, messages.size());

        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(Modification.EDIT, message.modification);
        Assert.assertEquals("Hi example!", onlyContent.body);
    }

    @Test
    public void correctionAfterOriginal() {

        final var messageWithTypo = new Message();
        messageWithTypo.setId("1");
        messageWithTypo.setTo(ACCOUNT);
        messageWithTypo.setFrom(REMOTE.withResource("junit"));
        messageWithTypo.addExtension(new Body()).setContent("Hii example!");

        this.transformer.transform(
                Transformation.of(messageWithTypo, Instant.now(), REMOTE, "stanza-a", null));

        Assert.assertEquals(1, database.messageDao().getMessages(1L).size());

        final var messageCorrection = new Message();
        messageCorrection.setId("2");
        messageCorrection.setTo(ACCOUNT);
        messageCorrection.setFrom(REMOTE.withResource("junit"));
        messageCorrection.addExtension(new Body()).setContent("Hi example!");
        messageCorrection.addExtension(new Replace()).setId("1");

        this.transformer.transform(
                Transformation.of(messageCorrection, Instant.now(), REMOTE, "stanza-b", null));

        final var messages = database.messageDao().getMessages(1L);

        Assert.assertEquals(1, messages.size());

        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(Modification.EDIT, message.modification);
        Assert.assertEquals("Hi example!", onlyContent.body);
    }
}
