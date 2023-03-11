package im.conversations.android.xmpp;

import static org.hamcrest.Matchers.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.Iterables;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.StanzaId;
import im.conversations.android.transformer.MessageTransformation;
import im.conversations.android.xmpp.manager.ArchiveManager;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ArchivePagingTest extends BaseTransformationTest {

    @Test
    public void initialQuery() throws ExecutionException, InterruptedException {
        final var ranges = database.archiveDao().resetLivePage(account(), ACCOUNT);
        final Range range = Iterables.getOnlyElement(ranges);
        Assert.assertNull(range.id);
        Assert.assertEquals(Range.Order.REVERSE, range.order);
    }

    @Test
    public void queryAfterSingleLiveMessage() throws ExecutionException, InterruptedException {
        final var stub = new StubMessage(2);
        transformer.transform(stub.messageTransformation(), stub.stanzaId());
        final var ranges = database.archiveDao().resetLivePage(account(), ACCOUNT);
        Assert.assertEquals(2, ranges.size());
        MatcherAssert.assertThat(
                ranges,
                contains(new Range(Range.Order.REVERSE, "2"), new Range(Range.Order.NORMAL, "2")));
    }

    @Test
    public void twoLiveMessageQueryNoSubmitAndQuery()
            throws ExecutionException, InterruptedException {
        final var stub2 = new StubMessage(2);
        transformer.transform(stub2.messageTransformation(), stub2.stanzaId());
        final var stub3 = new StubMessage(3);
        transformer.transform(stub3.messageTransformation(), stub3.stanzaId());

        final var ranges = database.archiveDao().resetLivePage(account(), ACCOUNT);
        Assert.assertEquals(2, ranges.size());
        MatcherAssert.assertThat(
                ranges,
                contains(new Range(Range.Order.REVERSE, "2"), new Range(Range.Order.NORMAL, "3")));

        final var stub4 = new StubMessage(4);
        transformer.transform(stub4.messageTransformation(), stub4.stanzaId());

        final var rangesSecondAttempt = database.archiveDao().resetLivePage(account(), ACCOUNT);
        Assert.assertEquals(2, rangesSecondAttempt.size());
        MatcherAssert.assertThat(
                rangesSecondAttempt,
                contains(new Range(Range.Order.REVERSE, "2"), new Range(Range.Order.NORMAL, "3")));
    }

    @Test
    public void liveMessageQuerySubmitAndQuery() throws ExecutionException, InterruptedException {
        final var stub2 = new StubMessage(2);
        transformer.transform(stub2.messageTransformation(), stub2.stanzaId());
        final var stub3 = new StubMessage(3);
        transformer.transform(stub3.messageTransformation(), stub3.stanzaId());

        final var ranges = database.archiveDao().resetLivePage(account(), ACCOUNT);
        Assert.assertEquals(2, ranges.size());
        MatcherAssert.assertThat(
                ranges,
                contains(new Range(Range.Order.REVERSE, "2"), new Range(Range.Order.NORMAL, "3")));

        final var stub4 = new StubMessage(4);
        transformer.transform(stub4.messageTransformation(), stub4.stanzaId());

        for (final Range range : ranges) {
            database.archiveDao()
                    .submitPage(
                            account(),
                            ACCOUNT,
                            range,
                            new ArchiveManager.QueryResult(
                                    true, Page.emptyWithCount(range.id, null)),
                            false);
        }

        final var rangesSecondAttempt = database.archiveDao().resetLivePage(account(), ACCOUNT);
        // we mark the reversing range as complete in the submit above; hence it is not included in
        // the second ranges
        Assert.assertEquals(1, rangesSecondAttempt.size());
        MatcherAssert.assertThat(rangesSecondAttempt, contains(new Range(Range.Order.NORMAL, "4")));
    }

    private Account account() throws ExecutionException, InterruptedException {
        return this.database.accountDao().getEnabledAccount(ACCOUNT).get();
    }

    private static class StubMessage {
        public final int id;

        private StubMessage(int id) {
            this.id = id;
        }

        public StanzaId stanzaId() {
            return new StanzaId(String.valueOf(id), ACCOUNT);
        }

        public MessageTransformation messageTransformation() {
            final var message = new Message();
            message.setTo(ACCOUNT);
            message.setFrom(REMOTE);
            message.addExtension(new Body()).setContent(String.format("%s (%d)", GREETING, id));
            return MessageTransformation.of(
                    message,
                    Instant.ofEpochSecond(id * 2000L),
                    REMOTE,
                    String.valueOf(id),
                    message.getFrom().asBareJid(),
                    null);
        }
    }
}
