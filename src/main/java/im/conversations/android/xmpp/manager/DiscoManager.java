package im.conversations.android.xmpp.manager;

import android.content.Context;
import android.os.Build;
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
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.Hash;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.version.Version;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiscoManager extends AbstractManager {

    public static final String CAPABILITY_NODE = "http://conversations.im";
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoManager.class);
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

    private static final Collection<String> FEATURES_IMPACTING_PRIVACY =
            Collections.singleton(Namespace.VERSION);

    private static final Collection<String> FEATURES_NOTIFY =
            Arrays.asList(
                    Namespace.NICK,
                    Namespace.AVATAR_METADATA,
                    Namespace.BOOKMARKS2,
                    Namespace.AXOLOTL_DEVICE_LIST);

    public DiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public static EntityCapabilities.Hash buildHashFromNode(final String node) {
        final var capsPrefix = CAPABILITY_NODE + "#";
        final var caps2Prefix = Namespace.ENTITY_CAPABILITIES_2 + "#";
        if (node.startsWith(capsPrefix)) {
            final String hash = node.substring(capsPrefix.length());
            if (Strings.isNullOrEmpty(hash)) {
                return null;
            }
            if (BaseEncoding.base64().canDecode(hash)) {
                return EntityCapabilities.EntityCapsHash.of(hash);
            }
        } else if (node.startsWith(caps2Prefix)) {
            final String caps = node.substring(caps2Prefix.length());
            if (Strings.isNullOrEmpty(caps)) {
                return null;
            }
            final int separator = caps.lastIndexOf('.');
            if (separator < 0) {
                return null;
            }
            final Hash.Algorithm algorithm = Hash.Algorithm.tryParse(caps.substring(0, separator));
            final String hash = caps.substring(separator + 1);
            if (algorithm == null || Strings.isNullOrEmpty(hash)) {
                return null;
            }
            if (BaseEncoding.base64().canDecode(hash)) {
                return EntityCapabilities2.EntityCaps2Hash.of(algorithm, hash);
            }
        }
        return null;
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

    public boolean hasAccountFeature(final String feature) {
        return hasFeature(getAccount().address, feature);
    }

    public boolean hasServerFeature(final String feature) {
        return hasFeature(getAccount().address.getDomain(), feature);
    }

    public ServiceDescription getServiceDescription() {
        return getServiceDescription(isPrivacyModeEnabled());
    }

    private ServiceDescription getServiceDescription(final boolean privacyMode) {
        final ImmutableList.Builder<String> builder = ImmutableList.builder();
        final List<String> features;
        builder.addAll(FEATURES_BASE);
        builder.addAll(
                Collections2.transform(FEATURES_NOTIFY, fn -> String.format("%s+notify", fn)));
        if (privacyMode) {
            features = builder.build();
        } else {
            features = builder.addAll(FEATURES_AV_CALLS).addAll(FEATURES_IMPACTING_PRIVACY).build();
        }
        return new ServiceDescription(
                features,
                new ServiceDescription.Identity(BuildConfig.APP_NAME, "client", getIdentityType()));
    }

    String getIdentityVersion() {
        return BuildConfig.VERSION_NAME;
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

    public void handleInfoQuery(final Iq request) {
        final var infoQueryRequest = request.getExtension(InfoQuery.class);
        final var nodeRequest = infoQueryRequest.getNode();
        LOGGER.warn("{} requested disco info for node {}", request.getFrom(), nodeRequest);
        final ServiceDescription serviceDescription;
        if (Strings.isNullOrEmpty(nodeRequest)) {
            serviceDescription = getServiceDescription();
        } else {
            final var hash = buildHashFromNode(nodeRequest);
            final var cachedServiceDescription =
                    hash != null
                            ? getManager(PresenceManager.class).getCachedServiceDescription(hash)
                            : null;
            if (cachedServiceDescription != null) {
                serviceDescription = cachedServiceDescription;
            } else {
                LOGGER.warn("No disco info was cached for node {}", nodeRequest);
                connection.sendErrorFor(request, new Condition.ItemNotFound());
                return;
            }
        }
        final var infoQuery = serviceDescription.asInfoQuery();
        infoQuery.setNode(nodeRequest);
        connection.sendResultFor(request, infoQuery);
    }

    public void handleVersion(final Iq request) {
        if (isPrivacyModeEnabled()) {
            connection.sendErrorFor(request, new Condition.ServiceUnavailable());
        } else {
            final var version = new Version();
            version.setSoftwareName(BuildConfig.APP_NAME);
            version.setVersion(BuildConfig.VERSION_NAME);
            version.setOs(String.format("Android %s", Build.VERSION.RELEASE));
            connection.sendResultFor(request, version);
        }
    }

    private boolean isPrivacyModeEnabled() {
        return false;
    }
}
