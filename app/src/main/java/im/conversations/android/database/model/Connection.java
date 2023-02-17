package im.conversations.android.database.model;

public class Connection {

    public final String hostname;
    public final int port;
    public final boolean directTls;

    public Connection(final String hostname, final int port, final boolean directTls) {
        this.hostname = hostname;
        this.port = port;
        this.directTls = directTls;
    }
}
