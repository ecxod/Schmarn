package im.conversations.android.axolotl;

import android.annotation.SuppressLint;
import android.os.Build;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import im.conversations.android.Conversations;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.axolotl.Header;
import im.conversations.android.xmpp.model.axolotl.Key;
import im.conversations.android.xmpp.model.axolotl.Payload;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;

public class EncryptionBuilder {

    private Long sourceDeviceId;

    private final ArrayList<AxolotlSession> sessions = new ArrayList<>();

    private byte[] payload;

    public Encrypted build() throws AxolotlEncryptionException {
        try {
            return buildOrThrow();
        } catch (final InvalidAlgorithmParameterException
                | NoSuchPaddingException
                | IllegalBlockSizeException
                | NoSuchAlgorithmException
                | BadPaddingException
                | NoSuchProviderException
                | InvalidKeyException
                | UntrustedIdentityException e) {
            throw new AxolotlEncryptionException(e);
        }
    }

    private Encrypted buildOrThrow()
            throws InvalidAlgorithmParameterException, NoSuchPaddingException,
                    IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException,
                    NoSuchProviderException, InvalidKeyException, UntrustedIdentityException {
        final long sourceDeviceId =
                Preconditions.checkNotNull(this.sourceDeviceId, "Specify a source device id");
        final var payloadCleartext = Preconditions.checkNotNull(this.payload, "Specify a payload");
        Preconditions.checkState(sessions.size() > 0, "Add at least on session");
        final var sessions = ImmutableList.copyOf(this.sessions);
        final var key = generateKey();
        final var iv = generateIv();
        final var encryptedPayload = encrypt(payloadCleartext, key, iv);
        final var keyWithAuthTag = new byte[32];
        System.arraycopy(key, 0, keyWithAuthTag, 0, key.length);
        System.arraycopy(
                encryptedPayload.authTag, 0, keyWithAuthTag, 16, encryptedPayload.authTag.length);
        final var header = buildHeader(sessions, keyWithAuthTag);
        header.addIv(iv);
        header.setSourceDevice(sourceDeviceId);
        final var encrypted = new Encrypted();
        encrypted.addExtension(header);
        final var payload = encrypted.addExtension(new Payload());
        payload.setContent(encryptedPayload.encrypted);
        return encrypted;
    }

    public Encrypted buildKeyTransport() throws AxolotlEncryptionException {
        try {
            return buildKeyTransportOrThrow();
        } catch (final UntrustedIdentityException e) {
            throw new AxolotlEncryptionException(e);
        }
    }

    private Encrypted buildKeyTransportOrThrow() throws UntrustedIdentityException {
        final long sourceDeviceId =
                Preconditions.checkNotNull(this.sourceDeviceId, "Specify a source device id");
        Preconditions.checkState(
                this.payload == null, "A key transport message should not have a payload");
        // TODO key transport messages in twomemo (omemo:1) use 32 bytes of zeros instead of a key
        // TODO if we are not using this using this for actual key transport we can do this in siacs
        // omemo too (and get rid of the IV)
        final var sessions = ImmutableList.copyOf(this.sessions);
        final var key = generateKey();
        final var iv = generateIv();
        final var header = buildHeader(sessions, key);
        header.addIv(iv);
        header.setSourceDevice(sourceDeviceId);
        final var encrypted = new Encrypted();
        encrypted.addExtension(header);
        return encrypted;
    }

    public EncryptionBuilder payload(final String payload) {
        this.payload = payload.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public EncryptionBuilder session(final AxolotlSession session) {
        this.sessions.add(session);
        return this;
    }

    public EncryptionBuilder sourceDeviceId(final long sourceDeviceId) {
        this.sourceDeviceId = sourceDeviceId;
        return this;
    }

    private Header buildHeader(List<AxolotlSession> sessions, final byte[] keyWithAuthTag)
            throws UntrustedIdentityException {
        final var header = new Header();
        for (final AxolotlSession session : sessions) {
            final var cipherMessage = session.sessionCipher.encrypt(keyWithAuthTag);
            final var key = header.addExtension(new Key());
            key.setRemoteDeviceId(session.axolotlAddress.getDeviceId());
            key.setContent(cipherMessage.serialize());
            key.setIsPreKey(cipherMessage.getType() == CiphertextMessage.PREKEY_TYPE);
        }
        return header;
    }

    @SuppressLint("DeprecatedProvider")
    private static EncryptedPayload encrypt(
            final byte[] payloadCleartext, final byte[] key, final byte[] iv)
            throws NoSuchPaddingException, NoSuchAlgorithmException, NoSuchProviderException,
                    IllegalBlockSizeException, BadPaddingException,
                    InvalidAlgorithmParameterException, InvalidKeyException {
        final Cipher cipher;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cipher = Cipher.getInstance(AxolotlService.CIPHER_MODE);
        } else {
            cipher =
                    Cipher.getInstance(
                            AxolotlService.CIPHER_MODE, AxolotlService.BOUNCY_CASTLE_PROVIDER);
        }
        final SecretKey secretKey = new SecretKeySpec(key, AxolotlService.KEY_TYPE);
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        final var encryptedWithAuthTag = cipher.doFinal(payloadCleartext);
        final var authTag = new byte[16];
        final var encrypted = new byte[encryptedWithAuthTag.length - authTag.length];

        System.arraycopy(encryptedWithAuthTag, 0, encrypted, 0, encrypted.length);
        System.arraycopy(encryptedWithAuthTag, encrypted.length, authTag, 0, authTag.length);
        return new EncryptedPayload(encrypted, authTag);
    }

    private static byte[] generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(AxolotlService.KEY_TYPE);
            generator.init(128);
            return generator.generateKey().getEncoded();
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static final class EncryptedPayload {
        public final byte[] encrypted;
        public final byte[] authTag;

        private EncryptedPayload(byte[] encrypted, byte[] authTag) {
            this.encrypted = encrypted;
            this.authTag = authTag;
        }
    }

    private static byte[] generateIv() {
        final byte[] iv = new byte[12];
        Conversations.SECURE_RANDOM.nextBytes(iv);
        return iv;
    }
}
