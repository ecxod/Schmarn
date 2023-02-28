package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jingle.OmemoVerification;
import eu.siacs.conversations.xmpp.jingle.OmemoVerifiedRtpContentMap;
import eu.siacs.conversations.xmpp.jingle.RtpContentMap;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.OmemoVerifiedIceUdpTransportInfo;
import im.conversations.android.axolotl.AxolotlAddress;
import im.conversations.android.axolotl.AxolotlDecryptionException;
import im.conversations.android.axolotl.AxolotlEncryptionException;
import im.conversations.android.axolotl.AxolotlPayload;
import im.conversations.android.axolotl.AxolotlService;
import im.conversations.android.axolotl.AxolotlSession;
import im.conversations.android.axolotl.EncryptionBuilder;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.axolotl.Bundle;
import im.conversations.android.xmpp.model.axolotl.DeviceList;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

public class AxolotlManager extends AbstractManager implements AxolotlService.PostDecryptionHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(AxolotlManager.class);

    private static final int NUM_PRE_KEYS_IN_BUNDLE = 30;

    private final AxolotlService axolotlService;

    public AxolotlManager(Context context, XmppConnection connection) {
        super(context, connection);
        this.axolotlService =
                new AxolotlService(
                        connection.getAccount(), ConversationsDatabase.getInstance(context));
        this.axolotlService.setPostDecryptionHook(this);
    }

    public AxolotlService getAxolotlService() {
        return this.axolotlService;
    }

    public void handleItems(final BareJid from, final Items items) {
        final var deviceList = items.getFirstItem(DeviceList.class);
        if (from == null || deviceList == null) {
            return;
        }
        final var deviceIds = deviceList.getDeviceIds();
        LOGGER.info("Received {} from {}", deviceIds, from);
        getDatabase().axolotlDao().setDeviceList(getAccount(), from, deviceIds);
    }

    public ListenableFuture<Set<Integer>> fetchDeviceIds(final BareJid address) {
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

    public ListenableFuture<AxolotlSession> getOrCreateSessionCipher(
            final AxolotlAddress axolotlAddress) {
        final AxolotlSession session = axolotlService.getExistingSession(axolotlAddress);
        if (session != null) {
            return Futures.immediateFuture(session);
        } else {
            final var bundleFuture =
                    fetchBundle(axolotlAddress.getJid(), axolotlAddress.getDeviceId());
            return Futures.transform(
                    bundleFuture,
                    bundle -> {
                        final var identityKey = buildSession(axolotlAddress, bundle);
                        return AxolotlSession.of(
                                signalProtocolStore(), identityKey, axolotlAddress);
                    },
                    MoreExecutors.directExecutor());
        }
    }

    private IdentityKey buildSession(final AxolotlAddress address, final Bundle bundle) {
        final var sessionBuilder = new SessionBuilder(signalProtocolStore(), address);
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
        final var signalIdentityKey = new IdentityKey(identityKey.asECPublicKey());
        final var preKeyBundle =
                new PreKeyBundle(
                        0,
                        deviceId,
                        preKey.getId(),
                        preKey.asECPublicKey(),
                        signedPreKey.getId(),
                        signedPreKey.asECPublicKey(),
                        signedPreKeySignature.asBytes(),
                        signalIdentityKey);
        try {
            sessionBuilder.process(preKeyBundle);
            return signalIdentityKey;
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
        final var currentDeviceIdsFuture =
                Futures.transform(
                        getManager(PepManager.class)
                                .fetchMostRecentItem(
                                        Namespace.AXOLOTL_DEVICE_LIST, DeviceList.class),
                        DeviceList::getDeviceIds,
                        MoreExecutors.directExecutor());
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
        return getManager(PepManager.class)
                .publishSingleton(
                        deviceList, Namespace.AXOLOTL_DEVICE_LIST, NodeConfiguration.OPEN);
    }

    private ListenableFuture<Void> publishBundle() {
        final ListenableFuture<Bundle> bundleFuture = prepareBundle();
        return Futures.transformAsync(
                bundleFuture,
                bundle -> {
                    final var node =
                            String.format(
                                    Locale.ROOT,
                                    "%s:%d",
                                    Namespace.AXOLOTL_BUNDLES,
                                    signalProtocolStore().getLocalRegistrationId());
                    return getManager(PepManager.class)
                            .publishSingleton(bundle, node, NodeConfiguration.OPEN);
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Bundle> prepareBundle() {
        final var refillFuture = Futures.submit(this::refillPreKeys, CPU_EXECUTOR);
        return Futures.transform(
                refillFuture, this::prepareBundle, getDatabase().getQueryExecutor());
    }

    private Bundle prepareBundle(Void v) {
        final var bundle = new Bundle();
        bundle.setIdentityKey(
                signalProtocolStore().getIdentityKeyPair().getPublicKey().getPublicKey());
        final var signedPreKeyRecord =
                getDatabase().axolotlDao().getLatestSignedPreKey(getAccount().id);
        if (signedPreKeyRecord == null) {
            throw new IllegalStateException("No signed PreKeys have been created yet");
        }
        bundle.setSignedPreKey(
                signedPreKeyRecord.getId(),
                signedPreKeyRecord.getKeyPair().getPublicKey(),
                signedPreKeyRecord.getSignature());
        bundle.addPreKeys(getDatabase().axolotlDao().getPreKeys(getAccount().id));
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
                                signalProtocolStore().getIdentityKeyPair(), signedPreKeyId);
            } catch (final InvalidKeyException e) {
                throw new IllegalStateException("Could not generate SignedPreKey", e);
            }
            signalProtocolStore().storeSignedPreKey(signedPreKeyRecord.getId(), signedPreKeyRecord);
            LOGGER.info("Generated SignedPreKey #{}", signedPreKeyRecord.getId());
        }
        axolotlDao.setPreKeys(getAccount(), preKeys);
        if (count > 0) {
            LOGGER.info("Generated {} PreKeys starting with {}", preKeys.size(), start);
        }
    }

    private OmemoVerifiedIceUdpTransportInfo encrypt(
            final IceUdpTransportInfo element, final AxolotlSession session)
            throws AxolotlEncryptionException {
        final OmemoVerifiedIceUdpTransportInfo transportInfo =
                new OmemoVerifiedIceUdpTransportInfo();
        transportInfo.setAttributes(element.getAttributes());
        for (final Element child : element.getChildren()) {
            if ("fingerprint".equals(child.getName())
                    && Namespace.JINGLE_APPS_DTLS.equals(child.getNamespace())) {
                final Element fingerprint =
                        new Element("fingerprint", Namespace.OMEMO_DTLS_SRTP_VERIFICATION);
                fingerprint.setAttribute("setup", child.getAttribute("setup"));
                fingerprint.setAttribute("hash", child.getAttribute("hash"));
                final String content = child.getContent();
                final var encrypted =
                        new EncryptionBuilder()
                                .sourceDeviceId(signalProtocolStore().getLocalRegistrationId())
                                .payload(content)
                                .session(session)
                                .build();
                fingerprint.addExtension(encrypted);
                transportInfo.addChild(fingerprint);
            } else {
                transportInfo.addChild(child);
            }
        }
        return transportInfo;
    }

    public ListenableFuture<AxolotlService.OmemoVerifiedPayload<OmemoVerifiedRtpContentMap>>
            encrypt(final RtpContentMap rtpContentMap, final Jid jid, final int deviceId) {
        final var axolotlAddress = new AxolotlAddress(jid.asBareJid(), deviceId);
        final var sessionFuture = getOrCreateSessionCipher(axolotlAddress);
        return Futures.transformAsync(
                sessionFuture,
                session -> encrypt(rtpContentMap, session),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<AxolotlService.OmemoVerifiedPayload<OmemoVerifiedRtpContentMap>>
            encrypt(final RtpContentMap rtpContentMap, final AxolotlSession session) {
        if (Config.REQUIRE_RTP_VERIFICATION) {
            requireVerification(session);
        }
        final ImmutableMap.Builder<String, RtpContentMap.DescriptionTransport>
                descriptionTransportBuilder = new ImmutableMap.Builder<>();
        final OmemoVerification omemoVerification = new OmemoVerification();
        omemoVerification.setDeviceId(session.axolotlAddress.getDeviceId());
        omemoVerification.setSessionFingerprint(session.identityKey);
        for (final Map.Entry<String, RtpContentMap.DescriptionTransport> content :
                rtpContentMap.contents.entrySet()) {
            final RtpContentMap.DescriptionTransport descriptionTransport = content.getValue();
            final OmemoVerifiedIceUdpTransportInfo encryptedTransportInfo;
            try {
                encryptedTransportInfo = encrypt(descriptionTransport.transport, session);
            } catch (final AxolotlEncryptionException e) {
                return Futures.immediateFailedFuture(e);
            }
            descriptionTransportBuilder.put(
                    content.getKey(),
                    new RtpContentMap.DescriptionTransport(
                            descriptionTransport.senders,
                            descriptionTransport.description,
                            encryptedTransportInfo));
        }
        return Futures.immediateFuture(
                new AxolotlService.OmemoVerifiedPayload<>(
                        omemoVerification,
                        new OmemoVerifiedRtpContentMap(
                                rtpContentMap.group, descriptionTransportBuilder.build())));
    }

    public ListenableFuture<AxolotlService.OmemoVerifiedPayload<RtpContentMap>> decrypt(
            OmemoVerifiedRtpContentMap omemoVerifiedRtpContentMap, final Jid from) {
        final ImmutableMap.Builder<String, RtpContentMap.DescriptionTransport>
                descriptionTransportBuilder = new ImmutableMap.Builder<>();
        final OmemoVerification omemoVerification = new OmemoVerification();
        final ImmutableList.Builder<ListenableFuture<AxolotlSession>> pepVerificationFutures =
                new ImmutableList.Builder<>();
        for (final Map.Entry<String, RtpContentMap.DescriptionTransport> content :
                omemoVerifiedRtpContentMap.contents.entrySet()) {
            final RtpContentMap.DescriptionTransport descriptionTransport = content.getValue();
            final AxolotlService.OmemoVerifiedPayload<IceUdpTransportInfo> decryptedTransport;
            try {
                decryptedTransport =
                        decrypt(
                                (OmemoVerifiedIceUdpTransportInfo) descriptionTransport.transport,
                                from,
                                pepVerificationFutures);
            } catch (final AxolotlDecryptionException e) {
                return Futures.immediateFailedFuture(e);
            }
            omemoVerification.setOrEnsureEqual(decryptedTransport);
            descriptionTransportBuilder.put(
                    content.getKey(),
                    new RtpContentMap.DescriptionTransport(
                            descriptionTransport.senders,
                            descriptionTransport.description,
                            decryptedTransport.getPayload()));
        }
        final ImmutableList<ListenableFuture<AxolotlSession>> sessionFutures =
                pepVerificationFutures.build();
        return Futures.transform(
                Futures.allAsList(sessionFutures),
                sessions -> {
                    if (Config.REQUIRE_RTP_VERIFICATION) {
                        for (final AxolotlSession session : sessions) {
                            requireVerification(session);
                        }
                    }
                    return new AxolotlService.OmemoVerifiedPayload<>(
                            omemoVerification,
                            new RtpContentMap(
                                    omemoVerifiedRtpContentMap.group,
                                    descriptionTransportBuilder.build()));
                },
                MoreExecutors.directExecutor());
    }

    private AxolotlService.OmemoVerifiedPayload<IceUdpTransportInfo> decrypt(
            final OmemoVerifiedIceUdpTransportInfo verifiedIceUdpTransportInfo,
            final Jid from,
            ImmutableList.Builder<ListenableFuture<AxolotlSession>> pepVerificationFutures)
            throws AxolotlDecryptionException {
        final IceUdpTransportInfo transportInfo = new IceUdpTransportInfo();
        transportInfo.setAttributes(verifiedIceUdpTransportInfo.getAttributes());
        final OmemoVerification omemoVerification = new OmemoVerification();
        for (final Element child : verifiedIceUdpTransportInfo.getChildren()) {
            if ("fingerprint".equals(child.getName())
                    && Namespace.OMEMO_DTLS_SRTP_VERIFICATION.equals(child.getNamespace())) {
                final Element fingerprint = new Element("fingerprint", Namespace.JINGLE_APPS_DTLS);
                fingerprint.setAttribute("setup", child.getAttribute("setup"));
                fingerprint.setAttribute("hash", child.getAttribute("hash"));
                final Encrypted encrypted = child.getExtension(Encrypted.class);
                final AxolotlPayload axolotlPayload = axolotlService.decrypt(from, encrypted);
                fingerprint.setContent(axolotlPayload.payloadAsString());
                omemoVerification.setDeviceId(axolotlPayload.axolotlAddress.getDeviceId());
                omemoVerification.setSessionFingerprint(axolotlPayload.identityKey);
                transportInfo.addChild(fingerprint);
            } else {
                transportInfo.addChild(child);
            }
        }
        return new AxolotlService.OmemoVerifiedPayload<>(omemoVerification, transportInfo);
    }

    private static void requireVerification(final AxolotlSession session) {
        // TODO fix me; check if identity key is trusted

        /*if (session.getTrust().isVerified()) {
            return;
        }*/
        throw new AxolotlService.NotVerifiedException(
                String.format(
                        "session with %s was not verified", session.identityKey.getFingerprint()));
    }

    private SignalProtocolStore signalProtocolStore() {
        return this.axolotlService.getSignalProtocolStore();
    }

    @Override
    public void executeHook(final Set<AxolotlAddress> freshSessions) {
        for (final AxolotlAddress axolotlAddress : freshSessions) {
            LOGGER.info(
                    "fresh session from {}/{}",
                    axolotlAddress.getJid(),
                    axolotlAddress.getDeviceId());
        }
    }

    @Override
    public void executeHook(Multimap<BareJid, Integer> devicesNotInPep) {
        for (final Map.Entry<BareJid, Collection<Integer>> entries :
                devicesNotInPep.asMap().entrySet()) {
            if (entries.getValue().isEmpty()) {
                continue;
            }
            // Warning. This will leak our resource to anyone who knows our jid + device id
            // TODO we could limit this to addresses in our roster; however the point of this
            // exercise is mostly to improve reliability with people not in our roster
            confirmDeviceInPep(entries.getKey(), ImmutableSet.copyOf(entries.getValue()));
        }
    }

    private void confirmDeviceInPep(final BareJid address, final Set<Integer> devices) {
        final var deviceListFuture = this.fetchDeviceIds(address);
        final var caughtDeviceListFuture =
                Futures.catching(
                        deviceListFuture,
                        Exception.class,
                        (Function<Exception, Set<Integer>>) input -> Collections.emptySet(),
                        MoreExecutors.directExecutor());
        Futures.addCallback(
                caughtDeviceListFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Set<Integer> devicesInPep) {
                        final Set<Integer> unconfirmedDevices =
                                Sets.difference(devices, devicesInPep);
                        if (unconfirmedDevices.isEmpty()) {
                            return;
                        }
                        LOGGER.info(
                                "Found unconfirmed devices for {}: {}",
                                address,
                                unconfirmedDevices);
                        getDatabase()
                                .axolotlDao()
                                .setUnconfirmedDevices(getAccount(), address, unconfirmedDevices);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        LOGGER.error("Could not confirm device list for {}", address, throwable);
                    }
                },
                getDatabase().getQueryExecutor());
    }
}
