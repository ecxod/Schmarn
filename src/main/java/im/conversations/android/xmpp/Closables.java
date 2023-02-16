package im.conversations.android.xmpp;

import java.io.Closeable;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Closables {

    private static final Logger LOGGER = LoggerFactory.getLogger(Closables.class);

    private Closables() {}

    public static void close(final Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final IOException e) {
            LOGGER.warn("Could not close closable", e);
        }
    }
}
