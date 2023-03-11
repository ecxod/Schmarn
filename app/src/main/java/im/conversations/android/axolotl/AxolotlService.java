package im.conversations.android.axolotl;

import android.content.Context;
import android.os.Build;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import eu.siacs.conversations.xmpp.jingle.OmemoVerification;
import im.conversations.android.AbstractAccountService;
import im.conversations.android.database.AxolotlDatabaseStore;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.model.Account;
import im.conversations.android.transformer.MessageContentWrapper;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.axolotl.Header;
import im.conversations.android.xmpp.model.axolotl.Key;
import im.conversations.android.xmpp.model.axolotl.Payload;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;

public class AxolotlService extends AbstractAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AxolotlService.class);

    public static final String KEY_TYPE = "AES";
    public static final String CIPHER_MODE = "AES/GCM/NoPadding";

    public static final String BOUNCY_CASTLE_PROVIDER = "BC";

    private final SignalProtocolStore signalProtocolStore;

    private PostDecryptionHook postDecryptionHook;

    private final Set<AxolotlAddress> freshSessions = new HashSet<>();
    private final Multimap<BareJid, Integer> devicesNotInPep = ArrayListMultimap.create();

    public AxolotlService(
            final Account account,
            final Context context,
            final ConversationsDatabase conversationsDatabase) {
        super(account, conversationsDatabase);
        this.signalProtocolStore =
                new AxolotlDatabaseStore(account, context, conversationsDatabase);
    }

    public void setPostDecryptionHook(final PostDecryptionHook postDecryptionHook) {
        this.postDecryptionHook = postDecryptionHook;
    }

    private AxolotlSession buildReceivingSession(
            final Jid from, final IdentityKey identityKey, final Header header) {
        final Optional<Integer> sid = header.getSourceDevice();
        if (sid.isPresent()) {
            return AxolotlSession.of(
                    signalProtocolStore,
                    identityKey,
                    new AxolotlAddress(from.asBareJid(), sid.get()));
        }
        throw new IllegalArgumentException("Header did not contain a source device id");
    }

    public AxolotlSession getExistingSession(final AxolotlAddress axolotlAddress) {
        final SessionRecord sessionState = signalProtocolStore.loadSession(axolotlAddress);
        if (sessionState == null) {
            return null;
        }
        final IdentityKey identityKey = sessionState.getSessionState().getRemoteIdentityKey();
        return AxolotlSession.of(signalProtocolStore, identityKey, axolotlAddress);
    }

    private AxolotlSession getExistingSessionOrThrow(final AxolotlAddress axolotlAddress)
            throws NoSessionException {
        final var session = getExistingSession(axolotlAddress);
        if (session == null) {
            // TODO When receiving a message that is not an OMEMOKeyExchange from a device there is
            // no session with, clients SHOULD create a session with that device and notify it about
            // the new session by responding with an empty OMEMO message as per Sending a message.
            throw new NoSessionException(
                    String.format("No session for %s", axolotlAddress.toString()));
        }
        return session;
    }

    public boolean decryptEmptyMessage(final BareJid from, final Encrypted encrypted) {
        Preconditions.checkArgument(
                !encrypted.hasPayload(), "Use decryptToMessageContent to decrypt payload messages");
        try {
            final var payload = decrypt(from, encrypted);
            return !payload.hasPayload();
        } catch (final AxolotlDecryptionException e) {
            return false;
        }
    }

    public MessageContentWrapper decryptToMessageContent(
            final BareJid from, final Encrypted encrypted) {
        Preconditions.checkArgument(encrypted.hasPayload());
        try {
            return MessageContentWrapper.ofAxolotl(decrypt(from, encrypted));
        } catch (final AxolotlDecryptionException e) {
            return MessageContentWrapper.ofAxolotlException(e);
        }
    }

    public AxolotlPayload decrypt(final Jid from, final Encrypted encrypted)
            throws AxolotlDecryptionException {
        final AxolotlPayload axolotlPayload;
        try {
            axolotlPayload = decryptOrThrow(from, encrypted);
        } catch (final IllegalArgumentException
                | NotEncryptedForThisDeviceException
                | InvalidMessageException
                | InvalidVersionException
                | UntrustedIdentityException
                | DuplicateMessageException
                | InvalidKeyIdException
                | LegacyMessageException
                | InvalidKeyException
                | NoSessionException
                | OutdatedSenderException
                | NoSuchPaddingException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException
                | java.security.InvalidKeyException
                | IllegalBlockSizeException
                | BadPaddingException e) {
            throw new AxolotlDecryptionException(e);
        }
        registerForHook(axolotlPayload);
        return axolotlPayload;
    }

    private AxolotlPayload decryptOrThrow(final Jid from, final Encrypted encrypted)
            throws NotEncryptedForThisDeviceException, InvalidMessageException,
                    InvalidVersionException, UntrustedIdentityException, DuplicateMessageException,
                    InvalidKeyIdException, LegacyMessageException, InvalidKeyException,
                    NoSessionException, OutdatedSenderException, NoSuchPaddingException,
                    NoSuchAlgorithmException, NoSuchProviderException,
                    InvalidAlgorithmParameterException, java.security.InvalidKeyException,
                    IllegalBlockSizeException, BadPaddingException {
        final Payload payload = encrypted.getPayload();
        final Header header = encrypted.getHeader();
        final Key ourKey = header.getKey(signalProtocolStore.getLocalRegistrationId());
        if (ourKey == null) {
            throw new NotEncryptedForThisDeviceException();
        }
        final byte[] keyWithAuthTag;
        final AxolotlSession session;
        final boolean preKeyMessage;
        if (ourKey.isPreKey()) {
            final PreKeySignalMessage preKeySignalMessage =
                    new PreKeySignalMessage(ourKey.asBytes());
            preKeyMessage = true;
            session = buildReceivingSession(from, preKeySignalMessage.getIdentityKey(), header);
            keyWithAuthTag = session.sessionCipher.decrypt(preKeySignalMessage);
        } else {
            final SignalMessage signalMessage = new SignalMessage(ourKey.asBytes());
            preKeyMessage = false;
            session =
                    getExistingSessionOrThrow(
                            new AxolotlAddress(from.asBareJid(), header.getSourceDevice().get()));
            keyWithAuthTag = session.sessionCipher.decrypt(signalMessage);
        }
        final var inDeviceList = database.axolotlDao().hasDeviceId(account, session.axolotlAddress);
        if (payload == null) {
            return new AxolotlPayload(
                    session.axolotlAddress, session.identityKey, preKeyMessage, inDeviceList, null);
        }
        if (keyWithAuthTag.length < 32) {
            throw new OutdatedSenderException(
                    "Key did not contain auth tag. Sender needs to update their OMEMO client");
        }
        final byte[] key = new byte[16];
        final byte[] authTag = new byte[16];
        final byte[] iv = header.getIv();
        System.arraycopy(keyWithAuthTag, 0, key, 0, key.length);
        System.arraycopy(keyWithAuthTag, key.length, authTag, 0, authTag.length);
        final Cipher cipher;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cipher = Cipher.getInstance(CIPHER_MODE);
        } else {
            cipher = Cipher.getInstance(CIPHER_MODE, BOUNCY_CASTLE_PROVIDER);
        }
        final SecretKey secretKey = new SecretKeySpec(key, KEY_TYPE);
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        final byte[] payloadAsBytes = payload.asBytes();
        final byte[] payloadWithAuthTag = new byte[payloadAsBytes.length + 16];
        System.arraycopy(payloadAsBytes, 0, payloadWithAuthTag, 0, payloadAsBytes.length);
        System.arraycopy(authTag, 0, payloadWithAuthTag, payloadAsBytes.length, authTag.length);
        final byte[] decryptedPayload = cipher.doFinal(payloadWithAuthTag);
        return new AxolotlPayload(
                session.axolotlAddress,
                session.identityKey,
                preKeyMessage,
                inDeviceList,
                decryptedPayload);
    }

    private void registerForHook(final AxolotlPayload axolotlPayload) {
        synchronized (this.freshSessions) {
            if (axolotlPayload.preKeyMessage) {
                this.freshSessions.add(axolotlPayload.axolotlAddress);
            }
        }
        synchronized (this.devicesNotInPep) {
            if (!axolotlPayload.inDeviceList) {
                this.devicesNotInPep.put(
                        axolotlPayload.axolotlAddress.getJid(),
                        axolotlPayload.axolotlAddress.getDeviceId());
            }
        }
    }

    public void executePostDecryptionHook() {
        final var hook = this.postDecryptionHook;
        if (hook == null) {
            return;
        }
        final Set<AxolotlAddress> freshSessions;
        synchronized (this.freshSessions) {
            freshSessions = ImmutableSet.copyOf(this.freshSessions);
            this.freshSessions.clear();
        }
        final Multimap<BareJid, Integer> devicesNotInPep;
        synchronized (this.devicesNotInPep) {
            devicesNotInPep = ImmutableMultimap.copyOf(this.devicesNotInPep);
        }
        hook.executeHook(freshSessions);
        hook.executeHook(devicesNotInPep);
    }

    public SignalProtocolStore getSignalProtocolStore() {
        return this.signalProtocolStore;
    }

    public static class OmemoVerifiedPayload<T> {
        private final int deviceId;
        private final IdentityKey identityKey;
        private final T payload;

        public OmemoVerifiedPayload(OmemoVerification omemoVerification, T payload) {
            this.deviceId = omemoVerification.getDeviceId();
            this.identityKey = omemoVerification.getFingerprint();
            this.payload = payload;
        }

        public int getDeviceId() {
            return deviceId;
        }

        public IdentityKey getFingerprint() {
            return identityKey;
        }

        public T getPayload() {
            return payload;
        }
    }

    public static class NotVerifiedException extends SecurityException {

        public NotVerifiedException(String message) {
            super(message);
        }
    }

    public interface PostDecryptionHook {
        void executeHook(final Set<AxolotlAddress> freshSessions);

        void executeHook(final Multimap<BareJid, Integer> devicesNotInPep);
    }
}
