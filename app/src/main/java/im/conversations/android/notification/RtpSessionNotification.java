package im.conversations.android.notification;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import im.conversations.android.R;
import im.conversations.android.database.model.Account;
import im.conversations.android.service.RtpSessionService;
import im.conversations.android.transformer.CallLogTransformation;
import im.conversations.android.ui.activity.RtpSessionActivity;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtpSessionNotification extends AbstractNotification {

    private static final Logger LOGGER = LoggerFactory.getLogger(RtpSessionNotification.class);

    public static final int INCOMING_CALL_ID = 2;

    public static final int LED_COLOR = 0xff00ff00;

    private static final long[] CALL_PATTERN = {0, 500, 300, 600};

    private Ringtone currentlyPlayingRingtone = null;
    private ScheduledFuture<?> vibrationFuture;

    public RtpSessionNotification(Context context) {
        super(context);
    }

    public void cancelIncomingCallNotification() {
        stopSoundAndVibration();
        cancel(INCOMING_CALL_ID);
    }

    public boolean stopSoundAndVibration() {
        int stopped = 0;
        if (this.currentlyPlayingRingtone != null) {
            if (this.currentlyPlayingRingtone.isPlaying()) {
                Log.d(Config.LOGTAG, "stop playing ring tone");
                ++stopped;
            }
            this.currentlyPlayingRingtone.stop();
        }
        if (this.vibrationFuture != null && !this.vibrationFuture.isCancelled()) {
            Log.d(Config.LOGTAG, "stop vibration");
            this.vibrationFuture.cancel(true);
            ++stopped;
        }
        return stopped > 0;
    }

    private void notify(int id, Notification notification) {
        final var notificationManager = NotificationManagerCompat.from(context);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                LOGGER.warn("Lacking notification permission");
                return;
            }
            notificationManager.notify(id, notification);
        } catch (final RuntimeException e) {
            LOGGER.warn("Could not post notification", e);
        }
    }

    private void cancel(final int notificationId) {
        final var notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(notificationId);
    }

    public synchronized void startRinging(
            final Account account, final AbstractJingleConnection.Id id, final Set<Media> media) {
        showIncomingCallNotification(account, id, media);
        final NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        final int currentInterruptionFilter;
        if (notificationManager != null) {
            currentInterruptionFilter = notificationManager.getCurrentInterruptionFilter();
        } else {
            currentInterruptionFilter = 1; // INTERRUPTION_FILTER_ALL
        }
        if (currentInterruptionFilter != 1) {
            Log.d(
                    Config.LOGTAG,
                    "do not ring or vibrate because interruption filter has been set to "
                            + currentInterruptionFilter);
            return;
        }
        final ScheduledFuture<?> currentVibrationFuture = this.vibrationFuture;
        this.vibrationFuture =
                SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(
                        new VibrationRunnable(), 0, 3, TimeUnit.SECONDS);
        if (currentVibrationFuture != null) {
            currentVibrationFuture.cancel(true);
        }
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final String ringtonePreference =
                preferences.getString(
                        "call_ringtone", resources.getString(R.string.incoming_call_ringtone));
        if (Strings.isNullOrEmpty(ringtonePreference)) {
            Log.d(Config.LOGTAG, "ringtone has been set to none");
            return;
        }
        final Uri uri = Uri.parse(ringtonePreference);
        this.currentlyPlayingRingtone = RingtoneManager.getRingtone(context, uri);
        if (this.currentlyPlayingRingtone == null) {
            Log.d(Config.LOGTAG, "unable to find ringtone for uri " + uri);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            this.currentlyPlayingRingtone.setLooping(true);
        }
        this.currentlyPlayingRingtone.play();
    }

    private void showIncomingCallNotification(
            final Account account, final AbstractJingleConnection.Id id, final Set<Media> media) {
        final Intent fullScreenIntent = new Intent(context, RtpSessionActivity.class);
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.id);
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_WITH, id.with.toString());
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.sessionId);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context, Channels.INCOMING_CALLS_NOTIFICATION_CHANNEL);
        if (media.contains(Media.VIDEO)) {
            builder.setSmallIcon(R.drawable.ic_videocam_24dp);
            builder.setContentTitle(context.getString(R.string.rtp_state_incoming_video_call));
        } else {
            builder.setSmallIcon(R.drawable.ic_call_24dp);
            builder.setContentTitle(context.getString(R.string.rtp_state_incoming_call));
        }
        // TODO fix me once we have a contact model
        /*final Contact contact = id.getContact();
        builder.setLargeIcon(
                mXmppConnectionService
                        .getAvatarService()
                        .get(contact, AvatarService.getSystemUiAvatarSize(mXmppConnectionService)));
        final Uri systemAccount = contact.getSystemAccount();
        if (systemAccount != null) {
            builder.addPerson(systemAccount.toString());
        }
        builder.setContentText(id.account.getRoster().getContact(id.with).getDisplayName());*/
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
        PendingIntent pendingIntent = createPendingRtpSession(account, id, Intent.ACTION_VIEW, 101);
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setContentIntent(pendingIntent); // old androids need this?
        builder.setOngoing(true);
        builder.addAction(
                new NotificationCompat.Action.Builder(
                                R.drawable.ic_call_end_24dp,
                                context.getString(R.string.dismiss_call),
                                createCallAction(
                                        id.sessionId, RtpSessionService.ACTION_DISMISS_CALL, 102))
                        .build());
        builder.addAction(
                new NotificationCompat.Action.Builder(
                                R.drawable.ic_call_24dp,
                                context.getString(R.string.answer_call),
                                createPendingRtpSession(
                                        account, id, RtpSessionActivity.ACTION_ACCEPT_CALL, 103))
                        .build());
        modifyIncomingCall(builder);
        final Notification notification = builder.build();
        notification.flags = notification.flags | Notification.FLAG_INSISTENT;
        notify(INCOMING_CALL_ID, notification);
    }

    public Notification getOngoingCallNotification(final Account account, OngoingCall ongoingCall) {
        final AbstractJingleConnection.Id id = ongoingCall.id;
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "ongoing_calls");
        if (ongoingCall.media.contains(Media.VIDEO)) {
            builder.setSmallIcon(R.drawable.ic_videocam_24dp);
            if (ongoingCall.reconnecting) {
                builder.setContentTitle(context.getString(R.string.reconnecting_video_call));
            } else {
                builder.setContentTitle(context.getString(R.string.ongoing_video_call));
            }
        } else {
            builder.setSmallIcon(R.drawable.ic_call_24dp);
            if (ongoingCall.reconnecting) {
                builder.setContentTitle(context.getString(R.string.reconnecting_call));
            } else {
                builder.setContentTitle(context.getString(R.string.ongoing_call));
            }
        }
        // TODO fix me when we have a Contact model
        // builder.setContentText(id.account.getRoster().getContact(id.with).getDisplayName());
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
        builder.setContentIntent(createPendingRtpSession(account, id, Intent.ACTION_VIEW, 101));
        builder.setOngoing(true);
        builder.addAction(
                new NotificationCompat.Action.Builder(
                                R.drawable.ic_call_end_24dp,
                                context.getString(R.string.hang_up),
                                createCallAction(
                                        id.sessionId, RtpSessionService.ACTION_END_CALL, 104))
                        .build());
        return builder.build();
    }

    private PendingIntent createPendingRtpSession(
            final Account account,
            final AbstractJingleConnection.Id id,
            final String action,
            final int requestCode) {
        final Intent fullScreenIntent = new Intent(context, RtpSessionActivity.class);
        fullScreenIntent.setAction(action);
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.id);
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_WITH, id.with.toString());
        fullScreenIntent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.sessionId);
        return PendingIntent.getActivity(
                context,
                requestCode,
                fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createCallAction(String sessionId, final String action, int requestCode) {
        final Intent intent = new Intent(context, RtpSessionService.class);
        intent.setAction(action);
        intent.setPackage(context.getPackageName());
        intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, sessionId);
        return PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void modifyIncomingCall(final NotificationCompat.Builder mBuilder) {
        mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        setNotificationColor(mBuilder);
        mBuilder.setLights(LED_COLOR, 2000, 3000);
    }

    private void setNotificationColor(final NotificationCompat.Builder mBuilder) {
        mBuilder.setColor(ContextCompat.getColor(context, R.color.seed));
    }

    public void pushMissedCallNow(CallLogTransformation message) {}

    private class VibrationRunnable implements Runnable {

        @Override
        public void run() {
            final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(CALL_PATTERN, -1);
        }
    }
}
