package im.conversations.android.xmpp;

import androidx.annotation.StringRes;
import eu.siacs.conversations.R;

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

    // TODO refactor into DataBinder (we can print the enum directly in the UI)
    @StringRes
    public int getReadableId() {
        switch (this) {
            case ONLINE:
                return R.string.account_status_online;
            case CONNECTING:
                return R.string.account_status_connecting;
            case OFFLINE:
                return R.string.account_status_offline;
            case UNAUTHORIZED:
                return R.string.account_status_unauthorized;
            case SERVER_NOT_FOUND:
                return R.string.account_status_not_found;
            case REGISTRATION_FAILED:
                return R.string.account_status_regis_fail;
            case REGISTRATION_WEB:
                return R.string.account_status_regis_web;
            case REGISTRATION_CONFLICT:
                return R.string.account_status_regis_conflict;
            case REGISTRATION_SUCCESSFUL:
                return R.string.account_status_regis_success;
            case REGISTRATION_NOT_SUPPORTED:
                return R.string.account_status_regis_not_sup;
            case REGISTRATION_INVALID_TOKEN:
                return R.string.account_status_regis_invalid_token;
            case TLS_ERROR:
                return R.string.account_status_tls_error;
            case TLS_ERROR_DOMAIN:
                return R.string.account_status_tls_error_domain;
            case INCOMPATIBLE_SERVER:
                return R.string.account_status_incompatible_server;
            case INCOMPATIBLE_CLIENT:
                return R.string.account_status_incompatible_client;
            case TOR_NOT_AVAILABLE:
                return R.string.account_status_tor_unavailable;
            case BIND_FAILURE:
                return R.string.account_status_bind_failure;
            case SESSION_FAILURE:
                return R.string.session_failure;
            case DOWNGRADE_ATTACK:
                return R.string.sasl_downgrade;
            case HOST_UNKNOWN:
                return R.string.account_status_host_unknown;
            case POLICY_VIOLATION:
                return R.string.account_status_policy_violation;
            case REGISTRATION_PLEASE_WAIT:
                return R.string.registration_please_wait;
            case REGISTRATION_PASSWORD_TOO_WEAK:
                return R.string.registration_password_too_weak;
            case STREAM_ERROR:
                return R.string.account_status_stream_error;
            case STREAM_OPENING_ERROR:
                return R.string.account_status_stream_opening_error;
            case PAYMENT_REQUIRED:
                return R.string.payment_required;
            case MISSING_INTERNET_PERMISSION:
                return R.string.missing_internet_permission;
            case TEMPORARY_AUTH_FAILURE:
                return R.string.account_status_temporary_auth_failure;
            default:
                return R.string.account_status_unknown;
        }
    }
}
