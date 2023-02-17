package im.conversations.android.database.model;

import com.google.common.base.Preconditions;

public class Proxy {

    public final Type type;
    public final String hostname;
    public final int port;

    public Proxy(final Type type, final String hostname, final int port) {
        Preconditions.checkNotNull(type);
        Preconditions.checkNotNull(hostname);
        this.type = type;
        this.hostname = hostname;
        this.port = port;
    }

    public enum Type {
        SOCKS5
    }
}
