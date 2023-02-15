package im.conversations.android;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import im.conversations.android.xmpp.ConnectionPool;
import java.security.SecureRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Conversations extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(Conversations.class);

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionPool.getInstance(this).reconfigure();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
