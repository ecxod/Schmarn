package im.conversations.android.service;

import android.content.Intent;
import androidx.lifecycle.LifecycleService;

public class RtpSessionService extends LifecycleService {

    public static final String ACTION_DISMISS_CALL = "dismiss_call";
    public static final String ACTION_END_CALL = "end_call";

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        return super.onStartCommand(intent, flags, startId);
    }
}
