package im.conversations.android.notification;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import im.conversations.android.R;

public final class Channels {

    static final String CHANNEL_FOREGROUND = "foreground";
    static final String CHANNEL_INCOMING_CALL = "incoming_calls_channel";
    static final String CHANNEL_ONGOING_CALL = "ongoing_call";
    static final String CHANNEL_GROUP_STATUS = "status";
    static final String CHANNEL_GROUP_CALLS = "calls";
    private final Application application;

    public Channels(final Application application) {
        this.application = application;
    }

    public void initialize() {
        final var notificationManager =
                ContextCompat.getSystemService(application, NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.initializeGroups(notificationManager);
            this.initializeForegroundChannel(notificationManager);

            this.initializeIncomingCallChannel(notificationManager);
            this.initializeOngoingCallChannel(notificationManager);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeOngoingCallChannel(NotificationManager notificationManager) {
        final NotificationChannel ongoingCallsChannel =
                new NotificationChannel(
                        CHANNEL_ONGOING_CALL,
                        application.getString(R.string.ongoing_calls_channel_name),
                        NotificationManager.IMPORTANCE_LOW);
        ongoingCallsChannel.setShowBadge(false);
        ongoingCallsChannel.setGroup(CHANNEL_GROUP_CALLS);
        notificationManager.createNotificationChannel(ongoingCallsChannel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeGroups(NotificationManager notificationManager) {
        notificationManager.createNotificationChannelGroup(
                new NotificationChannelGroup(
                        CHANNEL_GROUP_STATUS,
                        application.getString(R.string.notification_group_status_information)));
        notificationManager.createNotificationChannelGroup(
                new NotificationChannelGroup(
                        CHANNEL_GROUP_CALLS,
                        application.getString(R.string.notification_group_calls)));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeForegroundChannel(final NotificationManager notificationManager) {
        final NotificationChannel foregroundServiceChannel =
                new NotificationChannel(
                        CHANNEL_FOREGROUND,
                        application.getString(R.string.foreground_service_channel_name),
                        NotificationManager.IMPORTANCE_MIN);
        foregroundServiceChannel.setDescription(
                application.getString(
                        R.string.foreground_service_channel_description,
                        application.getString(R.string.app_name)));
        foregroundServiceChannel.setShowBadge(false);
        foregroundServiceChannel.setGroup(CHANNEL_GROUP_STATUS);
        notificationManager.createNotificationChannel(foregroundServiceChannel);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void initializeIncomingCallChannel(final NotificationManager notificationManager) {
        final NotificationChannel incomingCallsChannel =
                new NotificationChannel(
                        CHANNEL_INCOMING_CALL,
                        application.getString(R.string.incoming_calls_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
        incomingCallsChannel.setSound(null, null);
        incomingCallsChannel.setShowBadge(false);
        incomingCallsChannel.setLightColor(RtpSessionNotification.LED_COLOR);
        incomingCallsChannel.enableLights(true);
        incomingCallsChannel.setGroup(CHANNEL_GROUP_CALLS);
        incomingCallsChannel.setBypassDnd(true);
        incomingCallsChannel.enableVibration(false);
        notificationManager.createNotificationChannel(incomingCallsChannel);
    }
}
