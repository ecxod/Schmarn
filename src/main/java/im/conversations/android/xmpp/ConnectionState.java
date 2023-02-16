package im.conversations.android.xmpp;

public enum ConnectionState {
    OFFLINE(false),
    CONNECTING(false),
    ONLINE(false),
    UNAUTHORIZED,
    TEMPORARY_AUTH_FAILURE,
    SERVER_NOT_FOUND,
    REGISTRATION_SUCCESSFUL(false),
    REGISTRATION_FAILED(true, false),
    REGISTRATION_WEB(true, false),
    REGISTRATION_CONFLICT(true, false),
    REGISTRATION_NOT_SUPPORTED(true, false),
    REGISTRATION_PLEASE_WAIT(true, false),
    REGISTRATION_INVALID_TOKEN(true, false),
    REGISTRATION_PASSWORD_TOO_WEAK(true, false),
    TLS_ERROR,
    TLS_ERROR_DOMAIN,
    INCOMPATIBLE_SERVER,
    INCOMPATIBLE_CLIENT,
    TOR_NOT_AVAILABLE,
    DOWNGRADE_ATTACK,
    SESSION_FAILURE,
    BIND_FAILURE,
    HOST_UNKNOWN,
    STREAM_ERROR,
    STREAM_OPENING_ERROR,
    POLICY_VIOLATION,
    PAYMENT_REQUIRED,
    MISSING_INTERNET_PERMISSION(false);

    private final boolean isError;
    private final boolean attemptReconnect;

    ConnectionState(final boolean isError) {
        this(isError, true);
    }

    ConnectionState(final boolean isError, final boolean reconnect) {
        this.isError = isError;
        this.attemptReconnect = reconnect;
    }

    ConnectionState() {
        this(true, true);
    }

    public boolean isError() {
        return this.isError;
    }

    public boolean isAttemptReconnect() {
        return this.attemptReconnect;
    }
}
