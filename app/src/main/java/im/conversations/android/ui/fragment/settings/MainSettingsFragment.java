package im.conversations.android.ui.fragment.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import im.conversations.android.BuildConfig;
import im.conversations.android.R;

public class MainSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        final var about = findPreference("about");
        if (about == null) {
            throw new IllegalStateException("The preference resource file did not 'about'");
        }
        about.setTitle(getString(R.string.title_activity_about_x, BuildConfig.APP_NAME));
        about.setSummary(String.format("%s %s", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME));
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.title_activity_settings);
    }
}
