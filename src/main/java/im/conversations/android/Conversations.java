package im.conversations.android;

import android.app.Application;
import im.conversations.android.xmpp.ConnectionPool;
import java.security.SecureRandom;

public class Conversations extends Application {

    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionPool.getInstance(this).reconfigure();
    }
}
