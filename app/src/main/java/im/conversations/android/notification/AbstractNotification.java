package im.conversations.android.notification;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import im.conversations.android.R;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public abstract class AbstractNotification {

    protected static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE =
            Executors.newSingleThreadScheduledExecutor();

    protected final Context context;

    protected AbstractNotification(final Context context) {
        this.context = context;
    }

    public boolean notificationsFromStrangers() {
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(
                "notifications_from_strangers",
                context.getResources().getBoolean(R.bool.notifications_from_strangers));
    }
}
