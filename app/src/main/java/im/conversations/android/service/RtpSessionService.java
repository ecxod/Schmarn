package im.conversations.android.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import im.conversations.android.notification.RtpSessionNotification;
import im.conversations.android.ui.activity.RtpSessionActivity;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.manager.JingleConnectionManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtpSessionService extends LifecycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RtpSessionService.class);

    public static final String ACTION_REJECT_CALL = "dismiss_call";
    public static final String ACTION_END_CALL = "end_call";

    public static final String ACTION_UPDATE_ONGOING_CALL = "update_ongoing_call";

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null) {
            LOGGER.info("Intent was null");
            return Service.START_NOT_STICKY;
        }
        final String sessionId = intent.getStringExtra(RtpSessionActivity.EXTRA_SESSION_ID);
        final long accountId = intent.getLongExtra(RtpSessionActivity.EXTRA_ACCOUNT, -1);
        final String with = intent.getStringExtra(RtpSessionActivity.EXTRA_WITH);
        if (Strings.isNullOrEmpty(sessionId) || accountId < 0 || Strings.isNullOrEmpty(with)) {
            LOGGER.warn("intent was missing mandatory extras");
            return Service.START_NOT_STICKY;
        }
        final String action = intent.getAction();
        switch (Strings.nullToEmpty(action)) {
            case ACTION_UPDATE_ONGOING_CALL:
                updateOngoingCall(accountId, JidCreate.fromOrThrowUnchecked(with), sessionId);
                break;
            case ACTION_REJECT_CALL:
                rejectCall(accountId, JidCreate.fromOrThrowUnchecked(with), sessionId);
                break;
            case ACTION_END_CALL:
                endCall(accountId, JidCreate.fromOrThrowUnchecked(with), sessionId);
                break;
            default:
                LOGGER.error("Service does not know how to handle {} action", action);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void endCall(final long account, final Jid with, final String sessionId) {
        final var jmc =
                ConnectionPool.getInstance(this)
                        .getOptional(account)
                        .transform(xc -> xc.getManager(JingleConnectionManager.class));
        if (jmc.isPresent()) {
            endCall(jmc.get(), with, sessionId);
        } else {
            LOGGER.error("Could not end call. JingleConnectionManager not found");
        }
    }

    private void endCall(
            final JingleConnectionManager jingleConnectionManager,
            final Jid with,
            final String sessionId) {
        final var rtpConnection = jingleConnectionManager.getJingleRtpConnection(with, sessionId);
        if (rtpConnection.isPresent()) {
            rtpConnection.get().endCall();
        } else {
            LOGGER.error("Could not end {} with {}", sessionId, with);
        }
    }

    private void rejectCall(long account, Jid with, String sessionId) {
        final var jmc =
                ConnectionPool.getInstance(this)
                        .getOptional(account)
                        .transform(xc -> xc.getManager(JingleConnectionManager.class));
        if (jmc.isPresent()) {
            rejectCall(jmc.get(), with, sessionId);
        } else {
            LOGGER.error("Could not reject call. JingleConnectionManager not found");
        }
    }

    private void rejectCall(
            JingleConnectionManager jingleConnectionManager, Jid with, String sessionId) {
        final var rtpConnection = jingleConnectionManager.getJingleRtpConnection(with, sessionId);
        if (rtpConnection.isPresent()) {
            rtpConnection.get().rejectCall();
        } else {
            LOGGER.error("Could not reject call {} with {}", sessionId, with);
        }
    }

    private void updateOngoingCall(final long account, final Jid with, final String sessionId) {
        final var jmc =
                ConnectionPool.getInstance(this)
                        .getOptional(account)
                        .transform(xc -> xc.getManager(JingleConnectionManager.class));
        if (jmc.isPresent()) {
            updateOngoingCall(jmc.get(), with, sessionId);
        } else {
            LOGGER.error("JingleConnectionManager not found for {}", account);
        }
    }

    private void updateOngoingCall(
            final JingleConnectionManager jingleConnectionManager,
            final Jid with,
            final String sessionId) {
        final var ongoingCall =
                jingleConnectionManager
                        .getJingleRtpConnection(with, sessionId)
                        .transform(JingleRtpConnection::getOngoingCall);
        if (ongoingCall.isPresent()) {
            LOGGER.info("Updating notification for {}", ongoingCall.get());
            ForegroundService.stop(this);
            startForeground(
                    RtpSessionNotification.ONGOING_CALL_ID,
                    jingleConnectionManager
                            .getNotificationService()
                            .getOngoingCallNotification(
                                    jingleConnectionManager.getAccount(), ongoingCall.get()));
        } else {
            LOGGER.error("JingleRtpConnection not found for {}", sessionId);
        }
    }

    public static void updateOngoingCall(
            final Context context, final long account, final AbstractJingleConnection.Id id) {
        final var intent = new Intent(context, RtpSessionService.class);
        intent.setAction(ACTION_UPDATE_ONGOING_CALL);
        intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account);
        intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.with.toString());
        intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.sessionId);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(final Context context) {
        final var intent = new Intent(context, RtpSessionService.class);
        context.stopService(intent);
        ForegroundService.startForegroundService(context);
    }
}
