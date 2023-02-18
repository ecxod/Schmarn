package im.conversations.android;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import im.conversations.android.dns.Resolver;
import im.conversations.android.notification.Channels;
import im.conversations.android.xmpp.ConnectionPool;
import java.security.SecureRandom;
import java.security.Security;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Conversations extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Conversations.class);

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (final Throwable throwable) {
            LOGGER.warn("Could not initialize security provider", throwable);
        }
        final var channels = new Channels(this);
        channels.initialize();
        Resolver.init(this);
        ConnectionPool.getInstance(this).reconfigure();
        AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); // For night mode theme
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
