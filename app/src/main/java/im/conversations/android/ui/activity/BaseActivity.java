package im.conversations.android.ui.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import im.conversations.android.Conversations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseActivity extends AppCompatActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseActivity.class);

    private Boolean isDynamicColors;

    @Override
    public void onStart() {
        super.onStart();
        final int desiredNightMode = Conversations.getDesiredNightMode(this);
        if (setDesiredNightMode(desiredNightMode)) {
            return;
        }
        final boolean isDynamicColors = Conversations.isDynamicColorsDesired(this);
        setDynamicColors(isDynamicColors);
    }

    public void setDynamicColors(final boolean isDynamicColors) {
        if (this.isDynamicColors == null) {
            this.isDynamicColors = isDynamicColors;
        } else {
            if (this.isDynamicColors != isDynamicColors) {
                LOGGER.info(
                        "Recreating {} because dynamic color setting has changed",
                        getClass().getSimpleName());
                recreate();
            }
        }
    }

    public boolean setDesiredNightMode(final int desiredNightMode) {
        if (desiredNightMode == AppCompatDelegate.getDefaultNightMode()) {
            return false;
        }
        AppCompatDelegate.setDefaultNightMode(desiredNightMode);
        LOGGER.info(
                "Recreating {} because desired night mode has changed", getClass().getSimpleName());
        recreate();
        return true;
    }
}
