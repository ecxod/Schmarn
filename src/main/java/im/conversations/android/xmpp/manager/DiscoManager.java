package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class DiscoManager extends AbstractManager {

    public static final String CAPABILITY_NODE = "http://conversations.im";

    private static final Collection<String> FEATURES_BASE =
            Arrays.asList(
                    Namespace.JINGLE,
                    Namespace.JINGLE_FILE_TRANSFER_3,
                    Namespace.JINGLE_FILE_TRANSFER_4,
                    Namespace.JINGLE_FILE_TRANSFER_5,
                    Namespace.JINGLE_TRANSPORTS_S5B,
                    Namespace.JINGLE_TRANSPORTS_IBB,
                    Namespace.JINGLE_ENCRYPTED_TRANSPORT,
                    Namespace.JINGLE_ENCRYPTED_TRANSPORT_OMEMO,
                    Namespace.MUC,
                    Namespace.CONFERENCE,
                    Namespace.OOB,
                    Namespace.ENTITY_CAPABILITIES,
                    Namespace.ENTITY_CAPABILITIES_2,
                    Namespace.DISCO_INFO,
                    Namespace.PING,
                    Namespace.VERSION,
                    Namespace.CHAT_STATES,
                    Namespace.LAST_MESSAGE_CORRECTION,
                    Namespace.DELIVERY_RECEIPTS);

    private static final Collection<String> FEATURES_AV_CALLS =
            Arrays.asList(
                    Namespace.JINGLE_TRANSPORT_ICE_UDP,
                    Namespace.JINGLE_FEATURE_AUDIO,
                    Namespace.JINGLE_FEATURE_VIDEO,
                    Namespace.JINGLE_APPS_RTP,
                    Namespace.JINGLE_APPS_DTLS,
                    Namespace.JINGLE_MESSAGE);

    private static final Collection<String> FEATURES_NOTIFY =
            Arrays.asList(Namespace.NICK, Namespace.AVATAR_METADATA, Namespace.BOOKMARKS2);

    public DiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<InfoQuery> info(final Entity entity) {
        return info(entity, null);
    }

    public ListenableFuture<Void> infoOrCache(
            final Entity entity, @Nullable final String node, final EntityCapabilities.Hash hash) {
        if (getDatabase().discoDao().set(getAccount(), entity, node, hash)) {
            return Futures.immediateFuture(null);
        }
        return Futures.transform(
                info(entity, node, hash), f -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<InfoQuery> info(
            @NonNull final Entity entity, @Nullable final String node) {
        return info(entity, node, null);
    }

    public ListenableFuture<InfoQuery> info(
            final Entity entity,
            @Nullable final String node,
            @Nullable final EntityCapabilities.Hash hash) {
        final var requestNode = hash != null && node != null ? hash.capabilityNode(node) : node;
        final var iqRequest = new Iq(Iq.Type.GET);
        iqRequest.setTo(entity.address);
        final InfoQuery infoQueryRequest = iqRequest.addExtension(new InfoQuery());
        if (requestNode != null) {
            infoQueryRequest.setNode(requestNode);
        }
        final var future = connection.sendIqPacket(iqRequest);
        // TODO we need to remove the disco info associated with $entity in case of failure
        // this might happen in (rather unlikely) scenarios where an item no longer speaks disco
        return Futures.transform(
                future,
                iqResult -> {
                    final var infoQuery = iqResult.getExtension(InfoQuery.class);
                    if (infoQuery == null) {
                        throw new IllegalStateException("Response did not have query child");
                    }
                    if (!Objects.equals(requestNode, infoQuery.getNode())) {
                        throw new IllegalStateException(
                                "Node in response did not match node in request");
                    }
                    final var caps = EntityCapabilities.hash(infoQuery);
                    final var caps2 = EntityCapabilities2.hash(infoQuery);
                    if (hash instanceof EntityCapabilities.EntityCapsHash) {
                        checkMatch(
                                (EntityCapabilities.EntityCapsHash) hash,
                                caps,
                                EntityCapabilities.EntityCapsHash.class);
                    }
                    if (hash instanceof EntityCapabilities2.EntityCaps2Hash) {
                        checkMatch(
                                (EntityCapabilities2.EntityCaps2Hash) hash,
                                caps2,
                                EntityCapabilities2.EntityCaps2Hash.class);
                    }
                    getDatabase()
                            .discoDao()
                            .set(getAccount(), entity, node, caps.hash, caps2.hash, infoQuery);
                    return infoQuery;
                },
                MoreExecutors.directExecutor());
    }

    private <H extends EntityCapabilities.Hash> void checkMatch(
            final H expected, final H was, final Class<H> clazz) {
        if (Arrays.equals(expected.hash, was.hash)) {
            return;
        }
        throw new IllegalStateException(
                String.format(
                        "%s mismatch. Expected %s was %s",
                        clazz.getSimpleName(),
                        BaseEncoding.base64().encode(expected.hash),
                        BaseEncoding.base64().encode(was.hash)));
    }

    public ListenableFuture<Collection<Item>> items(final Entity.DiscoItem entity) {
        return items(entity, null);
    }

    public ListenableFuture<Collection<Item>> items(
            @NonNull final Entity.DiscoItem entity, @Nullable final String node) {
        final var requestNode = Strings.emptyToNull(node);
        final var iqPacket = new Iq(Iq.Type.GET);
        iqPacket.setTo(entity.address);
        final ItemsQuery itemsQueryRequest = iqPacket.addExtension(new ItemsQuery());
        if (requestNode != null) {
            itemsQueryRequest.setNode(requestNode);
        }
        final var future = connection.sendIqPacket(iqPacket);
        return Futures.transform(
                future,
                iqResult -> {
                    final var itemsQuery = iqResult.getExtension(ItemsQuery.class);
                    if (itemsQuery == null) {
                        throw new IllegalStateException();
                    }
                    if (!Objects.equals(requestNode, itemsQuery.getNode())) {
                        throw new IllegalStateException(
                                "Node in response did not match node in request");
                    }
                    final var items = itemsQuery.getExtensions(Item.class);
                    final var validItems =
                            Collections2.filter(items, i -> Objects.nonNull(i.getJid()));
                    getDatabase().discoDao().set(getAccount(), entity, requestNode, validItems);
                    return validItems;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<List<InfoQuery>> itemsWithInfo(final Entity.DiscoItem entity) {
        final var itemsFutures = items(entity);
        return Futures.transformAsync(
                itemsFutures,
                items -> {
                    Collection<ListenableFuture<InfoQuery>> infoFutures =
                            Collections2.transform(
                                    items, i -> info(Entity.discoItem(i.getJid()), i.getNode()));
                    return Futures.allAsList(infoFutures);
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasFeature(final Jid entity, final String feature) {
        return getDatabase().discoDao().hasFeature(getAccount().id, entity, feature);
    }

    public boolean hasServerFeature(final String feature) {
        return hasFeature(getAccount().address.getDomain(), feature);
    }

    public InfoQuery getInfo() {
        return getInfo(false);
    }

    private InfoQuery getInfo(final boolean privacyMode) {
        final var infoQuery = new InfoQuery();
        final ImmutableList.Builder<String> stringFeatureBuilder = ImmutableList.builder();
        stringFeatureBuilder.addAll(FEATURES_BASE);
        stringFeatureBuilder.addAll(
                Collections2.transform(FEATURES_NOTIFY, fn -> String.format("%s+notify", fn)));
        if (!privacyMode) {
            stringFeatureBuilder.addAll(FEATURES_AV_CALLS);
        }
        final var stringFeatures = stringFeatureBuilder.build();
        final Collection<Feature> features =
                Collections2.transform(
                        stringFeatures,
                        sf -> {
                            final var feature = new Feature();
                            feature.setVar(sf);
                            return feature;
                        });
        infoQuery.addExtensions(features);
        final var identity = infoQuery.addExtension(new Identity());
        identity.setIdentityName(getIdentityName());
        identity.setCategory("client");
        identity.setType(getIdentityType());
        return infoQuery;
    }

    String getIdentityVersion() {
        return BuildConfig.VERSION_NAME;
    }

    String getIdentityName() {
        return BuildConfig.APP_NAME;
    }

    String getIdentityType() {
        if ("chromium".equals(android.os.Build.BRAND)) {
            return "pc";
        } else if (context.getResources().getBoolean(R.bool.is_device_table)) {
            return "tablet";
        } else {
            return "phone";
        }
    }
}
