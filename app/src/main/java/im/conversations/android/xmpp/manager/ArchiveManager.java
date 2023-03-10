package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.IDs;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.transformer.MessageTransformation;
import im.conversations.android.transformer.TransformationFactory;
import im.conversations.android.transformer.Transformer;
import im.conversations.android.xmpp.Page;
import im.conversations.android.xmpp.Range;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.delay.Delay;
import im.conversations.android.xmpp.model.mam.Fin;
import im.conversations.android.xmpp.model.mam.Query;
import im.conversations.android.xmpp.model.mam.Result;
import im.conversations.android.xmpp.model.rsm.Set;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveManager extends AbstractManager {

    private static final int MAX_ITEMS_PER_PAGE = 50;
    private static final int MAX_PAGES_REVERSING = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveManager.class);

    private final TransformationFactory transformationFactory;

    private final Map<QueryId, RunningQuery> runningQueryMap = new HashMap<>();

    public ArchiveManager(Context context, XmppConnection connection) {
        super(context, connection);
        this.transformationFactory = new TransformationFactory(context, connection);
    }

    public void handle(final Message message) {
        final var result = message.getExtension(Result.class);
        Preconditions.checkArgument(result != null, "The message needs to contain a MAM result");
        final var from = message.getFrom();
        final var stanzaId = result.getId();
        final var id = result.getQueryId();
        final var forwarded = result.getForwarded();
        if (forwarded == null || id == null || stanzaId == null) {
            LOGGER.info("Received invalid MAM result from {} ", from);
            return;
        }
        final var forwardedMessage = forwarded.getMessage();
        final var delay = forwarded.getExtension(Delay.class);
        final var receivedAt = delay == null ? null : delay.getStamp();
        if (forwardedMessage == null || receivedAt == null) {
            LOGGER.info("MAM result from {} is missing message or receivedAt (delay)", from);
            return;
        }
        final Jid archive = from == null ? connection.getBoundAddress().asBareJid() : from;
        final RunningQuery runningQuery;
        synchronized (this.runningQueryMap) {
            runningQuery = this.runningQueryMap.get(new QueryId(archive, id));
        }
        if (runningQuery == null) {
            LOGGER.info("Did not find running query for {}/{}", archive, id);
            return;
        }

        final var transformation =
                this.transformationFactory.create(forwardedMessage, stanzaId, receivedAt);
        // TODO only when there is something to transform
        runningQuery.addTransformation(transformation);
    }

    private ListenableFuture<Metadata> fetchMetadata(final Jid archive) {
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(archive);
        iq.addExtension(new im.conversations.android.xmpp.model.mam.Metadata());
        final var metadataFuture = connection.sendIqPacket(iq);
        return Futures.transform(
                metadataFuture,
                result -> {
                    final var metadata =
                            result.getExtension(
                                    im.conversations.android.xmpp.model.mam.Metadata.class);
                    if (metadata == null) {
                        throw new IllegalStateException("result did not contain metadata");
                    }
                    final var start = metadata.getStart();
                    final var end = metadata.getEnd();
                    if (start == null && end == null) {
                        return new Metadata(null, null);
                    }
                    final var startId = start == null ? null : start.getId();
                    final var endId = end == null ? null : end.getId();
                    if (Strings.isNullOrEmpty(startId) || Strings.isNullOrEmpty(endId)) {
                        throw new IllegalStateException("metadata had empty start or end id");
                    }
                    return new Metadata(startId, endId);
                },
                MoreExecutors.directExecutor());
    }

    public void query(final Jid archive, final List<Range> queryRanges) {
        final var future = queryAsFuture(archive, queryRanges);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<Stats> stats) {
                        LOGGER.info("Successfully queried {} {}", archive, stats);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.warn("Something went wrong querying {}", archive, throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<Stats>> queryAsFuture(
            final Jid archive, final List<Range> queryRanges) {
        final var queryFutures = Lists.transform(queryRanges, qr -> queryAsFuture(archive, qr));
        return Futures.allAsList(queryFutures);
    }

    private ListenableFuture<Stats> queryAsFuture(final Jid archive, final Range queryRange) {
        return queryAsFuture(archive, queryRange, Stats.begin());
    }

    private ListenableFuture<Stats> queryAsFuture(
            final Jid archive, final Range queryRange, final Stats stats) {
        final var queryId = new QueryId(archive, IDs.medium());
        final var runningQuery = new RunningQuery(queryRange);
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(archive);
        final var query = iq.addExtension(new Query());
        query.setQueryId(queryId.id);
        query.addExtension(Set.of(queryRange, MAX_ITEMS_PER_PAGE));
        synchronized (this.runningQueryMap) {
            this.runningQueryMap.put(queryId, runningQuery);
        }
        final var queryResultFuture = connection.sendIqPacket(iq);
        return Futures.transformAsync(
                queryResultFuture,
                result -> {
                    final var fin = result.getExtension(Fin.class);
                    if (fin == null) {
                        throw new IllegalStateException("Iq response is missing fin element");
                    }
                    final var set = fin.getExtension(Set.class);
                    if (set == null) {
                        throw new IllegalStateException("Fin element is missing set element");
                    }
                    final QueryResult queryResult;
                    if (set.isEmpty()) {
                        // we fake an empty page here because on catch up queries we the live page
                        // to be properly reconfigured
                        queryResult =
                                new QueryResult(
                                        true, Page.emptyWithCount(queryRange.id, set.getCount()));
                    } else {
                        queryResult = new QueryResult(fin.isComplete(), set.asPage());
                    }
                    return processQueryResponse(queryId, queryResult, stats);
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Stats> processQueryResponse(
            final QueryId queryId, final QueryResult queryResult, final Stats existingStats) {
        final RunningQuery runningQuery;
        synchronized (this.runningQueryMap) {
            runningQuery = this.runningQueryMap.remove(queryId);
        }
        if (runningQuery == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException(
                            String.format(
                                    "Could not find running query for %s/%s",
                                    queryId.archive, queryId.id)));
        }
        final var messageTransformations = runningQuery.transformationBuilder.build();
        final var stats = existingStats.countPage(messageTransformations.size());
        final boolean reachedMaxPagesReversing =
                runningQuery.queryRange.order == Range.Order.REVERSE
                        && stats.pages >= MAX_PAGES_REVERSING;
        final var database = ConversationsDatabase.getInstance(context);
        final var axolotlService = connection.getManager(AxolotlManager.class).getAxolotlService();
        final var transformer = new Transformer(getAccount(), database, axolotlService);

        transformer.transform(
                messageTransformations,
                queryId.archive,
                runningQuery.queryRange,
                queryResult,
                reachedMaxPagesReversing);

        if (queryResult.isComplete || reachedMaxPagesReversing) {
            return Futures.immediateFuture(stats);
        } else {
            final Range range = queryResult.nextPage(runningQuery.queryRange.order);
            return queryAsFuture(queryId.archive, range, stats);
        }
    }

    public static final class Metadata {
        public final String start;
        public final String end;

        public Metadata(String start, String end) {
            this.start = start;
            this.end = end;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this).add("start", start).add("end", end).toString();
        }
    }

    public final class QueryResult {
        public final boolean isComplete;
        public final Page page;

        public QueryResult(boolean isComplete, Page page) {
            this.isComplete = isComplete;
            this.page = page;
        }

        public Range nextPage(final Range.Order order) {
            if (isComplete) {
                throw new IllegalStateException("Query was complete. There is no next page");
            }
            if (order == Range.Order.NORMAL) {
                return new Range(Range.Order.NORMAL, page.last);
            } else if (order == Range.Order.REVERSE) {
                return new Range(Range.Order.REVERSE, page.first);
            } else {
                throw new IllegalStateException("Unknown order");
            }
        }

        @NonNull
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("isComplete", isComplete)
                    .add("page", page)
                    .toString();
        }
    }

    public static final class RunningQuery {
        public final Range queryRange;
        private final ImmutableList.Builder<MessageTransformation> transformationBuilder =
                new ImmutableList.Builder<>();

        public RunningQuery(final Range queryRange) {
            this.queryRange = queryRange;
        }

        public void addTransformation(final MessageTransformation messageTransformation) {
            this.transformationBuilder.add(messageTransformation);
        }
    }

    public static final class QueryId {
        public final Jid archive;
        public final String id;

        public QueryId(Jid archive, String id) {
            this.archive = archive;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QueryId queryId = (QueryId) o;
            return Objects.equal(archive, queryId.archive) && Objects.equal(id, queryId.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(archive, id);
        }
    }

    public static class Stats {
        public final int pages;
        public final int transformations;

        private Stats(int pages, int transformations) {
            this.pages = pages;
            this.transformations = transformations;
        }

        public static Stats begin() {
            return new Stats(0, 0);
        }

        public Stats countPage(final int transformations) {
            return new Stats(this.pages + 1, this.transformations + transformations);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("pages", pages)
                    .add("transformations", transformations)
                    .toString();
        }
    }
}
