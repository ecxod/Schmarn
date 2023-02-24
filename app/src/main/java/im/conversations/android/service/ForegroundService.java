package im.conversations.android.service;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import im.conversations.android.notification.ForegroundServiceNotification;
import im.conversations.android.notification.RtpSessionNotification;
import im.conversations.android.xmpp.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForegroundService extends LifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForegroundService.class);

    private final ForegroundServiceNotification foregroundServiceNotification =
            new ForegroundServiceNotification(this);

    @Override
    public void onCreate() {
        super.onCreate();
        final var pool = ConnectionPool.getInstance(this);
        startForeground(
                ForegroundServiceNotification.ID,
                foregroundServiceNotification.build(pool.buildSummary()));
        pool.setSummaryProcessor(this::onSummaryUpdated);
    }

    private void onSummaryUpdated(final ConnectionPool.Summary summary) {
        foregroundServiceNotification.update(summary);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOGGER.debug("Destroying service. Removing listeners");
        ConnectionPool.getInstance(this).setSummaryProcessor(null);
    }

    public static void start(final Context context) {
        if (RtpSessionNotification.isShowingOngoingCallNotification(context)) {
            LOGGER.info("Do not start foreground service. Ongoing call.");
            return;
        }
        startForegroundService(context);
    }

    static void startForegroundService(final Context context) {
        try {
            ContextCompat.startForegroundService(
                    context, new Intent(context, ForegroundService.class));
        } catch (final RuntimeException e) {
            LOGGER.error("Could not start foreground service", e);
        }
    }

    public static void stop(final Context context) {
        final var intent = new Intent(context, ForegroundService.class);
        context.stopService(intent);
    }
}
