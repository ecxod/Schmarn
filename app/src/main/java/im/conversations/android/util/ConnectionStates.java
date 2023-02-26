package im.conversations.android.util;

import androidx.annotation.StringRes;
import im.conversations.android.R;
import im.conversations.android.xmpp.ConnectionState;

public final class ConnectionStates {

    private ConnectionStates() {
        throw new IllegalStateException("Do not instantiate me");
    }

    @StringRes
    public static int toStringRes(final ConnectionState state) {
        switch (state) {
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
                throw new IllegalStateException(String.format("no string res for %s", state));
        }
    }
}
