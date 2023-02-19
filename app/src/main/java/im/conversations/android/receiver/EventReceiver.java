package im.conversations.android.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import im.conversations.android.service.ForegroundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventReceiver extends BroadcastReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        LOGGER.info("Received event {}", intent.getAction());
        ForegroundService.start(context);
    }
}
