package im.conversations.android.database.model;

import com.google.common.base.MoreObjects;

public class Connection {

    public final String hostname;
    public final int port;
    public final boolean directTls;

    public Connection(final String hostname, final int port, final boolean directTls) {
        this.hostname = hostname;
        this.port = port;
        this.directTls = directTls;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hostname", hostname)
                .add("port", port)
                .add("directTls", directTls)
                .toString();
    }
}
