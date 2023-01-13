package im.conversations.android;

import android.app.Application;
import im.conversations.android.xmpp.ConnectionPool;

public class Conversations extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ConnectionPool.getInstance(this).reconfigure();
    }
}
