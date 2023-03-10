package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import im.conversations.android.database.entity.ArchivePageEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.StanzaId;
import im.conversations.android.xmpp.Range;
import im.conversations.android.xmpp.manager.ArchiveManager;
import java.util.List;
import java.util.Objects;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Dao
public abstract class ArchiveDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveDao.class);

    @Transaction
    public List<Range> resetLivePage(final Account account, final Jid archive) {
        final var page =
                getPage(
                        account.id,
                        archive,
                        ArchivePageEntity.Type.START,
                        ArchivePageEntity.Type.MIDDLE);
        final var livePage = getPage(account.id, archive, ArchivePageEntity.Type.LIVE);
        if (page == null && livePage == null) {
            LOGGER.info("Emitting initial query for {}", archive);
            return ImmutableList.of(new Range(Range.Order.REVERSE, null));
        }
        final ImmutableList.Builder<Range> queryRangeBuilder = new ImmutableList.Builder<>();
        final boolean gapLess = page != null && livePage != null && page.end.equals(livePage.start);
        if (gapLess) {
            LOGGER.info("Page and live page for {} were gap-less", archive);
            page.end = livePage.end;
            insert(page);
            if (page.type != ArchivePageEntity.Type.START && !page.reachedMaxPages) {
                queryRangeBuilder.add(new Range(Range.Order.REVERSE, page.start));
            }
            queryRangeBuilder.add(new Range(Range.Order.NORMAL, livePage.end));
        } else if (page != null) {
            LOGGER.info("Ignoring live page for {}", archive);
            // this will simply ignore the last live page and overwrite it
            if (page.type != ArchivePageEntity.Type.START && !page.reachedMaxPages) {
                queryRangeBuilder.add(new Range(Range.Order.REVERSE, page.start));
            }
            queryRangeBuilder.add(new Range(Range.Order.NORMAL, page.end));
        } else {
            LOGGER.info("Converting live page into regular page for {}", archive);
            insert(
                    ArchivePageEntity.of(
                            account,
                            archive,
                            ArchivePageEntity.Type.MIDDLE,
                            livePage.start,
                            livePage.end,
                            false));
            queryRangeBuilder.add(new Range(Range.Order.REVERSE, livePage.start));
            queryRangeBuilder.add(new Range(Range.Order.NORMAL, livePage.end));
        }
        if (livePage != null) {
            delete(livePage);
        }
        return queryRangeBuilder.build();
    }

    public void submitPage(
            final Account account,
            final Jid archive,
            final Range range,
            final ArchiveManager.QueryResult queryResult,
            final boolean reachedMaxPagesReversing) {
        if (reachedMaxPagesReversing) {
            Preconditions.checkState(
                    range.order == Range.Order.REVERSE,
                    "reachedMaxPagesReversing can only be true when reversing");
        }
        final var isComplete = queryResult.isComplete;
        final var page = queryResult.page;

        final var existingPage =
                getPage(
                        account.id,
                        archive,
                        ArchivePageEntity.Type.START,
                        ArchivePageEntity.Type.MIDDLE);
        final boolean isStart = range.order == Range.Order.REVERSE && isComplete;
        if (existingPage == null) {
            insert(
                    ArchivePageEntity.of(
                            account,
                            archive,
                            isStart ? ArchivePageEntity.Type.START : ArchivePageEntity.Type.MIDDLE,
                            page.first,
                            page.last,
                            reachedMaxPagesReversing));
        } else {
            if (range.order == Range.Order.REVERSE) {
                Preconditions.checkState(
                        Objects.equals(range.id, existingPage.start),
                        "Reversing range did not match start of existing page");
                existingPage.start = page.first;
                existingPage.type =
                        isStart ? ArchivePageEntity.Type.START : ArchivePageEntity.Type.MIDDLE;
            } else if (range.order == Range.Order.NORMAL) {
                Preconditions.checkState(
                        Objects.equals(range.id, existingPage.end),
                        "Normal range did not match end of existing page");
                existingPage.end = page.last;
            } else {
                throw new IllegalStateException(String.format("Unknown order %s", range.order));
            }
            existingPage.reachedMaxPages = existingPage.reachedMaxPages || reachedMaxPagesReversing;
            insert(existingPage);
        }

        final boolean lastIsLive =
                (range.order == Range.Order.REVERSE && range.id == null)
                        || (range.order == Range.Order.NORMAL && queryResult.isComplete);
        if (lastIsLive) {
            final var existingLivePage = getPage(account.id, archive, ArchivePageEntity.Type.LIVE);
            if (existingLivePage != null) {
                existingLivePage.start = page.last;
            } else {
                insert(
                        ArchivePageEntity.of(
                                account,
                                archive,
                                ArchivePageEntity.Type.LIVE,
                                page.last,
                                page.last,
                                false));
            }
        }
    }

    public void setLivePageStanzaId(final Account account, final StanzaId stanzaId) {
        LOGGER.info("set live page stanza id {}", stanzaId);
        final var currentLivePage = getPage(account.id, stanzaId.by, ArchivePageEntity.Type.LIVE);
        if (currentLivePage != null) {
            currentLivePage.end = stanzaId.id;
            insert(currentLivePage);
        } else {
            insert(
                    ArchivePageEntity.of(
                            account,
                            stanzaId.by,
                            ArchivePageEntity.Type.LIVE,
                            stanzaId.id,
                            stanzaId.id,
                            false));
        }
    }

    @Delete
    protected abstract void delete(final ArchivePageEntity entity);

    @Upsert
    protected abstract void insert(final ArchivePageEntity entity);

    @Query(
            "SELECT * FROM archive_page WHERE accountId=:account AND archive=:archive AND type"
                    + " IN(:type)")
    protected abstract ArchivePageEntity getPage(
            long account, Jid archive, ArchivePageEntity.Type... type);
}
