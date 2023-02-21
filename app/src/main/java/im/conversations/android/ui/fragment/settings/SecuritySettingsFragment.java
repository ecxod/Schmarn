package im.conversations.android.ui.fragment.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import com.google.common.base.Strings;
import im.conversations.android.R;

public class SecuritySettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_security, rootKey);
        final var omemo = findPreference("omemo");
        if (omemo == null) {
            throw new IllegalStateException("The preference resource file did not contain omemo");
        }
        omemo.setSummaryProvider(
                preference -> {
                    final var sharedPreferences = preference.getSharedPreferences();
                    final String value;
                    if (sharedPreferences == null) {
                        value = null;
                    } else {
                        value =
                                sharedPreferences.getString(
                                        preference.getKey(),
                                        requireContext().getString(R.string.omemo_setting_default));
                    }
                    switch (Strings.nullToEmpty(value)) {
                        case "always":
                            return requireContext()
                                    .getString(R.string.pref_omemo_setting_summary_always);
                        case "default_off":
                            return requireContext()
                                    .getString(R.string.pref_omemo_setting_summary_default_off);
                        default:
                            return requireContext()
                                    .getString(R.string.pref_omemo_setting_summary_default_on);
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_title_security);
    }
}
