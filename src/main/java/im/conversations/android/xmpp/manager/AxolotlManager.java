package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.AxolotlDatabaseStore;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.axolotl.AxolotlAddress;
import im.conversations.android.xmpp.model.axolotl.Bundle;
import im.conversations.android.xmpp.model.axolotl.DeviceList;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

public class AxolotlManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AxolotlManager.class);

    private static final int NUM_PRE_KEYS_IN_BUNDLE = 30;

    private final SignalProtocolStore signalProtocolStore;

    public AxolotlManager(Context context, XmppConnection connection) {
        super(context, connection);
        this.signalProtocolStore = new AxolotlDatabaseStore(context, connection.getAccount());
    }

    public void handleItems(final Jid from, final Items items) {
        final var deviceList = items.getFirstItem(DeviceList.class);
        if (from == null || deviceList == null) {
            return;
        }
        final var deviceIds = deviceList.getDeviceIds();
        LOGGER.info("Received {} from {}", deviceIds, from);
        getDatabase().axolotlDao().setDeviceList(getAccount(), from, deviceIds);
    }

    public ListenableFuture<Set<Integer>> fetchDeviceIds(final Jid address) {
        final var deviceIdsFuture =
                Futures.transform(
                        getManager(PubSubManager.class)
                                .fetchMostRecentItem(
                                        address, Namespace.AXOLOTL_DEVICE_LIST, DeviceList.class),
                        DeviceList::getDeviceIds,
                        MoreExecutors.directExecutor());
        // TODO refactor callback into class
        Futures.addCallback(
                deviceIdsFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Set<Integer> deviceIds) {
                        getDatabase().axolotlDao().setDeviceList(getAccount(), address, deviceIds);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            return;
                        }
                        if (throwable instanceof IqErrorException) {
                            final var iqErrorException = (IqErrorException) throwable;
                            final var error = iqErrorException.getError();
                            final var condition = error == null ? null : error.getCondition();
                            if (condition != null) {
                                getDatabase()
                                        .axolotlDao()
                                        .setDeviceListError(getAccount(), address, condition);
                                return;
                            }
                        }
                        getDatabase().axolotlDao().setDeviceListParsingError(getAccount(), address);
                    }
                },
                MoreExecutors.directExecutor());
        return deviceIdsFuture;
    }

    public ListenableFuture<Bundle> fetchBundle(final Jid address, final int deviceId) {
        final var node = String.format(Locale.ROOT, "%s:%d", Namespace.AXOLOTL_BUNDLES, deviceId);
        return getManager(PubSubManager.class).fetchMostRecentItem(address, node, Bundle.class);
    }

    public ListenableFuture<SessionCipher> getOrCreateSessionCipher(
            final AxolotlAddress axolotlAddress) {
        if (signalProtocolStore.containsSession(axolotlAddress)) {
            return Futures.immediateFuture(new SessionCipher(signalProtocolStore, axolotlAddress));
        } else {
            final var bundleFuture =
                    fetchBundle(axolotlAddress.getJid(), axolotlAddress.getDeviceId());
            return Futures.transform(
                    bundleFuture,
                    bundle -> {
                        buildSession(axolotlAddress, bundle);
                        return new SessionCipher(signalProtocolStore, axolotlAddress);
                    },
                    MoreExecutors.directExecutor());
        }
    }

    private void buildSession(final AxolotlAddress address, final Bundle bundle) {
        final var sessionBuilder = new SessionBuilder(signalProtocolStore, address);
        final var deviceId = address.getDeviceId();
        final var preKey = bundle.getRandomPreKey();
        final var signedPreKey = bundle.getSignedPreKey();
        final var signedPreKeySignature = bundle.getSignedPreKeySignature();
        final var identityKey = bundle.getIdentityKey();
        if (preKey == null) {
            throw new IllegalArgumentException("No PreKey found in bundle");
        }
        if (signedPreKey == null) {
            throw new IllegalArgumentException("No signed PreKey found in bundle");
        }
        if (signedPreKeySignature == null) {
            throw new IllegalArgumentException("No signed PreKey signature found in bundle");
        }
        if (identityKey == null) {
            throw new IllegalArgumentException("No IdentityKey found in bundle");
        }
        final var preKeyBundle =
                new PreKeyBundle(
                        0,
                        deviceId,
                        preKey.getId(),
                        preKey.asECPublicKey(),
                        signedPreKey.getId(),
                        signedPreKey.asECPublicKey(),
                        signedPreKeySignature.asBytes(),
                        new IdentityKey(identityKey.asECPublicKey()));
        try {
            sessionBuilder.process(preKeyBundle);
        } catch (final InvalidKeyException | UntrustedIdentityException e) {
            throw new RuntimeException(e);
        }
    }

    public void publishIfNecessary() {
        final int myDeviceId = getAccount().getPublicDeviceIdInt();
        if (getDatabase()
                        .axolotlDao()
                        .hasDeviceId(getAccount().id, getAccount().address, myDeviceId)
                && getManager(DiscoManager.class)
                        .hasAccountFeature(Namespace.PUB_SUB_PERSISTENT_ITEMS)) {
            LOGGER.info(
                    "device id seems to be current and server supports persistent items. nothing"
                            + " to do");
            return;
        }
        final var future = publishBundleAndDeviceId();
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        LOGGER.info("Successfully published bundle and device ID {}", myDeviceId);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.warn(
                                "Could not publish bundle and device ID for account {} ",
                                getAccount().address,
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publishBundleAndDeviceId() {
        final ListenableFuture<Void> bundleFuture = publishBundle();
        return Futures.transformAsync(
                bundleFuture, ignored -> publishDeviceId(), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publishDeviceId() {
        final var currentDeviceIdsFuture = fetchDeviceIds(getAccount().address);
        final ListenableFuture<Set<Integer>> currentDeviceIdsWithFallback =
                Futures.catching(
                        currentDeviceIdsFuture,
                        Throwable.class,
                        throwable -> {
                            LOGGER.info(
                                    "No current device list found. Defaulting to empty", throwable);
                            return Collections.emptySet();
                        },
                        MoreExecutors.directExecutor());
        return Futures.transformAsync(
                currentDeviceIdsWithFallback,
                currentDeviceIds -> {
                    final var myDeviceId = getAccount().getPublicDeviceIdInt();
                    if (currentDeviceIds.contains(myDeviceId)) {
                        return Futures.immediateVoidFuture();
                    } else {
                        final var deviceIds =
                                new ImmutableSet.Builder<Integer>()
                                        .addAll(currentDeviceIds)
                                        .add(myDeviceId)
                                        .build();
                        return publishDeviceIds(deviceIds);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publishDeviceIds(final Collection<Integer> deviceIds) {
        final var deviceList = new DeviceList();
        deviceList.setDeviceIds(deviceIds);
        return getManager(PubSubManager.class)
                .publishSingleton(
                        getAccount().address,
                        deviceList,
                        Namespace.AXOLOTL_DEVICE_LIST,
                        NodeConfiguration.OPEN);
    }

    private ListenableFuture<Void> publishBundle() {
        final ListenableFuture<Bundle> bundleFuture =
                Futures.submit(this::prepareBundle, IO_EXECUTOR);
        return Futures.transformAsync(
                bundleFuture,
                bundle -> {
                    final var node =
                            String.format(
                                    Locale.ROOT,
                                    "%s:%d",
                                    Namespace.AXOLOTL_BUNDLES,
                                    signalProtocolStore.getLocalRegistrationId());
                    return getManager(PubSubManager.class)
                            .publishSingleton(
                                    getAccount().address, bundle, node, NodeConfiguration.OPEN);
                },
                MoreExecutors.directExecutor());
    }

    private Bundle prepareBundle() {
        refillPreKeys();
        final var bundle = new Bundle();
        bundle.setIdentityKey(
                signalProtocolStore.getIdentityKeyPair().getPublicKey().getPublicKey());
        final var signedPreKeyRecord =
                getDatabase().axolotlDao().getLatestSignedPreKey(getAccount().id);
        if (signedPreKeyRecord == null) {
            throw new IllegalStateException("No signed PreKeys have been created yet");
        }
        bundle.setSignedPreKey(
                signedPreKeyRecord.getKeyPair().getPublicKey(), signedPreKeyRecord.getSignature());
        bundle.setPreKeys(getDatabase().axolotlDao().getPreKeys(getAccount().id));
        return bundle;
    }

    private void refillPreKeys() {
        final var accountId = getAccount().id;
        final var axolotlDao = getDatabase().axolotlDao();
        final var existing = axolotlDao.getExistingPreKeyCount(accountId);
        final var max = axolotlDao.getMaxPreKeyId(accountId);
        final var count = NUM_PRE_KEYS_IN_BUNDLE - existing;
        final int start = max == null ? 0 : max + 1;
        final var preKeys = KeyHelper.generatePreKeys(start, count);
        final int signedPreKeyId = (start + count) / NUM_PRE_KEYS_IN_BUNDLE - 1;
        if (getDatabase().axolotlDao().hasNotSignedPreKey(getAccount().id, signedPreKeyId)) {
            final SignedPreKeyRecord signedPreKeyRecord;
            try {
                signedPreKeyRecord =
                        KeyHelper.generateSignedPreKey(
                                signalProtocolStore.getIdentityKeyPair(), signedPreKeyId);
            } catch (final InvalidKeyException e) {
                throw new IllegalStateException("Could not generate SignedPreKey", e);
            }
            signalProtocolStore.storeSignedPreKey(signedPreKeyRecord.getId(), signedPreKeyRecord);
            LOGGER.info("Generated SignedPreKey #{}", signedPreKeyRecord.getId());
        }
        axolotlDao.setPreKeys(getAccount(), preKeys);
        if (count > 0) {
            LOGGER.info("Generated {} PreKeys starting with {}", preKeys.size(), start);
        }
    }
}
