package im.conversations.android.ui.fragment.settings;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import im.conversations.android.Conversations;
import im.conversations.android.R;
import im.conversations.android.ui.activity.SettingsActivity;

public class InterfaceSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_interface, rootKey);
        final var themePreference = findPreference("theme");
        final var dynamicColors = findPreference("dynamic_colors");
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        if (newValue instanceof String) {
                            final String theme = (String) newValue;
                            final int desiredNightMode = Conversations.getDesiredNightMode(theme);
                            requireSettingsActivity().setDesiredNightMode(desiredNightMode);
                        }
                        return true;
                    });
        }
        if (dynamicColors != null) {
            dynamicColors.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(
                                @NonNull Preference preference, Object newValue) {
                            requireSettingsActivity()
                                    .setDynamicColors(Boolean.TRUE.equals(newValue));
                            return true;
                        }
                    });
        }
    }

    public SettingsActivity requireSettingsActivity() {
        final var activity = requireActivity();
        if (activity instanceof SettingsActivity) {
            return (SettingsActivity) activity;
        }
        throw new IllegalStateException(
                String.format(
                        "%s is not %s",
                        activity.getClass().getName(), SettingsActivity.class.getName()));
    }
}
