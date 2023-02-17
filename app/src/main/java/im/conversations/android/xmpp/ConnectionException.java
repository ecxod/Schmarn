package im.conversations.android.xmpp;

public class ConnectionException extends Exception {

    private final ConnectionState connectionState;

    public ConnectionException(ConnectionState state) {
        this.connectionState = state;
    }
}
