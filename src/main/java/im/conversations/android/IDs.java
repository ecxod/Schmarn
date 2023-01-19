package im.conversations.android;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.util.UUID;

public class IDs {

    private static final long UUID_VERSION_MASK = 4 << 12;

    public static String medium() {
        final var random = new byte[9];
        Conversations.SECURE_RANDOM.nextBytes(random);
        return BaseEncoding.base64Url().encode(random);
    }

    public static String tiny() {
        final var random = new byte[3];
        Conversations.SECURE_RANDOM.nextBytes(random);
        return BaseEncoding.base64Url().encode(random);
    }

    public static String tiny(final byte[] seed) {
        return BaseEncoding.base64Url().encode(slice(seed));
    }

    private static byte[] slice(final byte[] input) {
        if (input == null || input.length < 3) {
            return new byte[3];
        }
        try {
            return ByteSource.wrap(input).slice(0, 3).read();
        } catch (final IOException e) {
            return new byte[3];
        }
    }

    public static UUID uuid(final byte[] bytes) {
        Preconditions.checkArgument(bytes != null && bytes.length == 32);

        long msb = 0;
        long lsb = 0;

        msb |= (bytes[0x0] & 0xffL) << 56;
        msb |= (bytes[0x1] & 0xffL) << 48;
        msb |= (bytes[0x2] & 0xffL) << 40;
        msb |= (bytes[0x3] & 0xffL) << 32;
        msb |= (bytes[0x4] & 0xffL) << 24;
        msb |= (bytes[0x5] & 0xffL) << 16;
        msb |= (bytes[0x6] & 0xffL) << 8;
        msb |= (bytes[0x7] & 0xffL);

        lsb |= (bytes[0x8] & 0xffL) << 56;
        lsb |= (bytes[0x9] & 0xffL) << 48;
        lsb |= (bytes[0xa] & 0xffL) << 40;
        lsb |= (bytes[0xb] & 0xffL) << 32;
        lsb |= (bytes[0xc] & 0xffL) << 24;
        lsb |= (bytes[0xd] & 0xffL) << 16;
        lsb |= (bytes[0xe] & 0xffL) << 8;
        lsb |= (bytes[0xf] & 0xffL);

        msb = (msb & 0xffffffffffff0fffL) | UUID_VERSION_MASK; // set version
        lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L; // set variant
        return new UUID(msb, lsb);
    }

    public static byte[] seed() {
        final var random = new byte[32];
        Conversations.SECURE_RANDOM.nextBytes(random);
        return random;
    }
}
