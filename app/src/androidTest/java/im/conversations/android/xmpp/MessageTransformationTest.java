package im.conversations.android.xmpp;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Iterables;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Encryption;
import im.conversations.android.database.model.MessageEmbedded;
import im.conversations.android.database.model.Modification;
import im.conversations.android.database.model.PartType;
import im.conversations.android.transformer.MessageTransformation;
import im.conversations.android.transformer.Transformer;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.reactions.Reaction;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.receipts.Received;
import im.conversations.android.xmpp.model.reply.Reply;
import im.conversations.android.xmpp.model.retract.Retract;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

@RunWith(AndroidJUnit4.class)
public class MessageTransformationTest {

    private static final BareJid ACCOUNT = JidCreate.bareFromOrThrowUnchecked("user@example.com");
    private static final BareJid REMOTE = JidCreate.bareFromOrThrowUnchecked("juliet@example.com");

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
                new Transformer(database.accountDao().getEnabledAccount(id).get(), database);
    }

    @Test
    public void reactionBeforeOriginal() throws XmppStringprepException {
        final var reactionMessage = new Message();
        reactionMessage.setId("2");
        reactionMessage.setTo(ACCOUNT);
        reactionMessage.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        final var reactions = reactionMessage.addExtension(new Reactions());
        reactions.setId("1");
        final var reaction = reactions.addExtension(new Reaction());
        reaction.setContent("Y");
        this.transformer.transform(
                MessageTransformation.of(reactionMessage, Instant.now(), REMOTE, "stanza-b", null));
        final var originalMessage = new Message();
        originalMessage.setId("1");
        originalMessage.setTo(REMOTE);
        originalMessage.setFrom(JidCreate.fullFrom(ACCOUNT, Resourcepart.from(("junit"))));
        final var body = originalMessage.addExtension(new Body());
        body.setContent(GREETING);
        this.transformer.transform(
                MessageTransformation.of(originalMessage, Instant.now(), REMOTE, "stanza-a", null));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(GREETING, onlyContent.body);
        Assert.assertEquals(Encryption.CLEARTEXT,message.encryption);
        final var onlyReaction = Iterables.getOnlyElement(message.reactions);
        Assert.assertEquals("Y", onlyReaction.reaction);
        Assert.assertEquals(REMOTE, onlyReaction.reactionBy);
    }

    @Test
    public void multipleReactions() throws XmppStringprepException {
        final var group = JidCreate.bareFrom("a@group.example.com");
        final var message = new Message(Message.Type.GROUPCHAT);
        message.addExtension(new Body("Please give me a thumbs up"));
        message.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(message, Instant.now(), REMOTE, "stanza-a", "id-user-a"));

        final var reactionA = new Message(Message.Type.GROUPCHAT);
        reactionA.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionA.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionA, Instant.now(), REMOTE, "stanza-b", "id-user-b"));

        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-c")));
        reactionB.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionB, Instant.now(), REMOTE, "stanza-c", "id-user-c"));

        final var reactionC = new Message(Message.Type.GROUPCHAT);
        reactionC.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-d")));
        final var reactions = reactionC.addExtension(Reactions.to("stanza-a"));
        reactions.addExtension(new Reaction("Y"));
        reactions.addExtension(new Reaction("Z"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionC, Instant.now(), REMOTE, "stanza-d", "id-user-d"));

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
    public void correctionBeforeOriginal() throws XmppStringprepException {

        final var messageCorrection = new Message();
        messageCorrection.setId("2");
        messageCorrection.setTo(ACCOUNT);
        messageCorrection.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        messageCorrection.addExtension(new Body()).setContent("Hi example!");
        messageCorrection.addExtension(new Replace()).setId("1");

        this.transformer.transform(
                MessageTransformation.of(
                        messageCorrection, Instant.now(), REMOTE, "stanza-a", null));

        // the correction should not show up as a message
        Assert.assertEquals(0, database.messageDao().getMessages(1L).size());

        final var messageWithTypo = new Message();
        messageWithTypo.setId("1");
        messageWithTypo.setTo(ACCOUNT);
        messageWithTypo.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        messageWithTypo.addExtension(new Body()).setContent("Hii example!");

        this.transformer.transform(
                MessageTransformation.of(messageWithTypo, Instant.now(), REMOTE, "stanza-b", null));

        final var messages = database.messageDao().getMessages(1L);

        Assert.assertEquals(1, messages.size());

        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(Modification.CORRECTION, message.modification);
        Assert.assertEquals("Hi example!", onlyContent.body);
    }

    @Test
    public void correctionAfterOriginal() throws XmppStringprepException {

        final var messageWithTypo = new Message();
        messageWithTypo.setId("1");
        messageWithTypo.setTo(ACCOUNT);
        messageWithTypo.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        messageWithTypo.addExtension(new Body()).setContent("Hii example!");

        this.transformer.transform(
                MessageTransformation.of(messageWithTypo, Instant.now(), REMOTE, "stanza-a", null));

        Assert.assertEquals(1, database.messageDao().getMessages(1L).size());

        final var messageCorrection = new Message();
        messageCorrection.setId("2");
        messageCorrection.setTo(ACCOUNT);
        messageCorrection.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        messageCorrection.addExtension(new Body()).setContent("Hi example!");
        messageCorrection.addExtension(new Replace()).setId("1");

        this.transformer.transform(
                MessageTransformation.of(
                        messageCorrection, Instant.now(), REMOTE, "stanza-b", null));

        final var messages = database.messageDao().getMessages(1L);

        Assert.assertEquals(1, messages.size());

        final var message = Iterables.getOnlyElement(messages);
        final var onlyContent = Iterables.getOnlyElement(message.contents);
        Assert.assertEquals(Modification.CORRECTION, message.modification);
        Assert.assertEquals("Hi example!", onlyContent.body);
    }

    @Test
    public void replacingReactions() throws XmppStringprepException {
        final var group = JidCreate.bareFrom("a@group.example.com");
        final var message = new Message(Message.Type.GROUPCHAT);
        message.addExtension(new Body("Please give me a thumbs up"));
        message.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(message, Instant.now(), REMOTE, "stanza-a", "id-user-a"));

        final var reactionA = new Message(Message.Type.GROUPCHAT);
        reactionA.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionA.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("N"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionA, Instant.now(), REMOTE, "stanza-b", "id-user-b"));

        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionB.addExtension(Reactions.to("stanza-a")).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionB, Instant.now(), REMOTE, "stanza-c", "id-user-b"));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var dbMessage = Iterables.getOnlyElement(messages);
        Assert.assertEquals(1, dbMessage.reactions.size());
    }

    @Test
    public void twoCorrectionsOneReactionBeforeOriginalInGroupChat()
            throws XmppStringprepException {
        final var group = JidCreate.bareFrom("a@group.example.com");
        final var ogStanzaId = "og-stanza-id";
        final var ogMessageId = "og-message-id";

        // first correction
        final var m1 = new Message(Message.Type.GROUPCHAT);
        // m1.setId(ogMessageId);
        m1.addExtension(new Body("Please give me an thumbs up"));
        m1.addExtension(new Replace()).setId(ogMessageId);
        m1.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m1,
                        Instant.ofEpochMilli(2000),
                        REMOTE,
                        "irrelevant-stanza-id1",
                        "id-user-a"));

        // second correction
        final var m2 = new Message(Message.Type.GROUPCHAT);
        // m2.setId(ogMessageId);
        m2.addExtension(new Body("Please give me a thumbs up"));
        m2.addExtension(new Replace()).setId(ogMessageId);
        m2.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m2,
                        Instant.ofEpochMilli(3000),
                        REMOTE,
                        "irrelevant-stanza-id2",
                        "id-user-a"));

        // a reaction
        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionB.addExtension(Reactions.to(ogStanzaId)).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionB, Instant.now(), REMOTE, "irrelevant-stanza-id3", "id-user-b"));

        // the original message
        final var m4 = new Message(Message.Type.GROUPCHAT);
        m4.setId(ogMessageId);
        m4.addExtension(new Body("Please give me thumbs up"));
        m4.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m4, Instant.ofEpochMilli(1000), REMOTE, ogStanzaId, "id-user-a"));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var dbMessage = Iterables.getOnlyElement(messages);
        Assert.assertEquals(1, dbMessage.reactions.size());
        Assert.assertEquals(Modification.CORRECTION, dbMessage.modification);
        Assert.assertEquals(
                "Please give me a thumbs up", Iterables.getOnlyElement(dbMessage.contents).body);
    }

    @Test
    public void twoReactionsOneCorrectionBeforeOriginalInGroupChat()
            throws XmppStringprepException {
        final var group = JidCreate.bareFrom("a@group.example.com");
        final var ogStanzaId = "og-stanza-id";
        final var ogMessageId = "og-message-id";

        // first reaction
        final var reactionA = new Message(Message.Type.GROUPCHAT);
        reactionA.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionA.addExtension(Reactions.to(ogStanzaId)).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionA, Instant.now(), REMOTE, "irrelevant-stanza-id1", "id-user-b"));

        // second reaction
        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-c")));
        reactionB.addExtension(Reactions.to(ogStanzaId)).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionB, Instant.now(), REMOTE, "irrelevant-stanza-id2", "id-user-c"));

        // a correction
        final var m1 = new Message(Message.Type.GROUPCHAT);
        m1.addExtension(new Body("Please give me a thumbs up"));
        m1.addExtension(new Replace()).setId(ogMessageId);
        m1.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m1,
                        Instant.ofEpochMilli(2000),
                        REMOTE,
                        "irrelevant-stanza-id3",
                        "id-user-a"));

        // the original message
        final var m4 = new Message(Message.Type.GROUPCHAT);
        m4.setId(ogMessageId);
        m4.addExtension(new Body("Please give me thumbs up (Typo)"));
        m4.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m4, Instant.ofEpochMilli(1000), REMOTE, ogStanzaId, "id-user-a"));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var dbMessage = Iterables.getOnlyElement(messages);
        Assert.assertEquals(2, dbMessage.reactions.size());
        final var onlyReaction = Iterables.getOnlyElement(dbMessage.getAggregatedReactions());
        Assert.assertEquals(2L, (long) onlyReaction.getValue());
        Assert.assertEquals(Modification.CORRECTION, dbMessage.modification);
        Assert.assertEquals(
                "Please give me a thumbs up", Iterables.getOnlyElement(dbMessage.contents).body);
    }

    @Test
    public void twoReactionsInGroupChat() throws XmppStringprepException {
        final var group = JidCreate.bareFrom("a@group.example.com");
        final var ogStanzaId = "og-stanza-id";
        final var ogMessageId = "og-message-id";

        // the original message
        final var m4 = new Message(Message.Type.GROUPCHAT);
        m4.setId(ogMessageId);
        m4.addExtension(new Body("Please give me a thumbs up"));
        m4.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-a")));
        this.transformer.transform(
                MessageTransformation.of(
                        m4, Instant.ofEpochMilli(1000), REMOTE, ogStanzaId, "id-user-a"));

        // first reaction
        final var reactionA = new Message(Message.Type.GROUPCHAT);
        reactionA.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-b")));
        reactionA.addExtension(Reactions.to(ogStanzaId)).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionA, Instant.now(), REMOTE, "irrelevant-stanza-id1", "id-user-b"));

        // second reaction
        final var reactionB = new Message(Message.Type.GROUPCHAT);
        reactionB.setFrom(JidCreate.fullFrom(group, Resourcepart.from("user-c")));
        reactionB.addExtension(Reactions.to(ogStanzaId)).addExtension(new Reaction("Y"));
        this.transformer.transform(
                MessageTransformation.of(
                        reactionB, Instant.now(), REMOTE, "irrelevant-stanza-id2", "id-user-c"));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(1, messages.size());
        final var dbMessage = Iterables.getOnlyElement(messages);
        Assert.assertEquals(2, dbMessage.reactions.size());
        final var onlyReaction = Iterables.getOnlyElement(dbMessage.getAggregatedReactions());
        Assert.assertEquals(2L, (long) onlyReaction.getValue());
        Assert.assertEquals(Modification.ORIGINAL, dbMessage.modification);
        Assert.assertEquals(
                "Please give me a thumbs up", Iterables.getOnlyElement(dbMessage.contents).body);
    }

    @Test
    public void inReplyTo() throws XmppStringprepException {
        final var m1 = new Message();
        m1.setId("1");
        m1.setTo(ACCOUNT);
        m1.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        m1.addExtension(new Body("Hi. How are you?"));

        this.transformer.transform(
                MessageTransformation.of(m1, Instant.now(), REMOTE, "stanza-a", null));

        final var m2 = new Message();
        m2.setId("2");
        m2.setTo(REMOTE);
        m2.setFrom(ACCOUNT);
        m2.addExtension(new Body("I am fine."));
        final var reply = m2.addExtension(new Reply());
        reply.setId("1");
        reply.setTo(REMOTE);

        this.transformer.transform(
                MessageTransformation.of(m2, Instant.now(), REMOTE, "stanza-b", null));

        final var messages = database.messageDao().getMessages(1L);
        Assert.assertEquals(2, messages.size());
        final var response = Iterables.get(messages, 1);
        Assert.assertNotNull(response.inReplyToMessageEntityId);
        final MessageEmbedded embeddedMessage = response.inReplyTo;
        Assert.assertNotNull(embeddedMessage);
        Assert.assertEquals(REMOTE, embeddedMessage.fromBare);
        Assert.assertEquals(1L, embeddedMessage.contents.size());
        Assert.assertEquals(
                "Hi. How are you?", Iterables.getOnlyElement(embeddedMessage.contents).body);
    }

    @Test
    public void messageWithReceipt() throws XmppStringprepException {
        final var m1 = new Message();
        m1.setId("1");
        m1.setTo(REMOTE);
        m1.setFrom(JidCreate.fullFrom(ACCOUNT, Resourcepart.from("junit")));
        m1.addExtension(new Body("Hi. How are you?"));

        this.transformer.transform(MessageTransformation.of(m1, Instant.now(), REMOTE, null, null));

        final var m2 = new Message();
        m2.setTo(JidCreate.fullFrom(ACCOUNT, Resourcepart.from("junit")));
        m2.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        m2.addExtension(new Received()).setId("1");

        this.transformer.transform(MessageTransformation.of(m2, Instant.now(), REMOTE, null, null));

        final var messages = database.messageDao().getMessages(1L);
        final var message = Iterables.getOnlyElement(messages);

        Assert.assertEquals(1L, message.states.size());
    }

    @Test
    public void messageAndRetraction() throws XmppStringprepException {
        final var m1 = new Message();
        m1.setTo(ACCOUNT);
        m1.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        m1.setId("m1");
        m1.addExtension(new Body("It is raining outside"));

        this.transformer.transform(MessageTransformation.of(m1, Instant.now(), REMOTE, null, null));

        final var m2 = new Message();
        m2.setTo(ACCOUNT);
        m2.setFrom(JidCreate.fullFrom(REMOTE, Resourcepart.from("junit")));
        m2.addExtension(new Retract()).setId("m1");

        this.transformer.transform(MessageTransformation.of(m2, Instant.now(), REMOTE, null, null));

        final var messages = database.messageDao().getMessages(1L);
        final var message = Iterables.getOnlyElement(messages);
        Assert.assertEquals(Modification.RETRACTION, message.modification);
        Assert.assertEquals(PartType.RETRACTION, Iterables.getOnlyElement(message.contents).type);
    }
}
