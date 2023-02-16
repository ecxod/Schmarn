package im.conversations.android;

import android.app.Application;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import eu.siacs.conversations.Config;
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
        ConnectionPool.getInstance(this).reconfigure();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
