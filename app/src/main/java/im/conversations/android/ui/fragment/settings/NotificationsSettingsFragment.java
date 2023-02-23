package im.conversations.android.ui.fragment.settings;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.common.base.Strings;
import im.conversations.android.R;
import im.conversations.android.ui.activity.result.PickRingtone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationsSettingsFragment extends PreferenceFragmentCompat {

    private static final String RINGTONE_PREFERENCE_KEY = "call_ringtone";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(NotificationsSettingsFragment.class);

    private final ActivityResultLauncher<Uri> pickRingtoneLauncher =
            registerForActivityResult(
                    new PickRingtone(),
                    result -> {
                        if (result == null) {
                            // do nothing. user aborted
                            return;
                        }
                        final Uri uri = PickRingtone.noneToNull(result);
                        setRingtone(uri);
                        LOGGER.info("User set ringtone to {}", uri);
                    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_notifications, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.notifications);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (RINGTONE_PREFERENCE_KEY.equals(preference.getKey())) {
            pickRingtone();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void pickRingtone() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        final String incomingCallRingtone =
                sharedPreferences.getString(
                        RINGTONE_PREFERENCE_KEY,
                        requireContext().getString(R.string.incoming_call_ringtone));
        final Uri uri =
                Strings.isNullOrEmpty(incomingCallRingtone)
                        ? null
                        : Uri.parse(incomingCallRingtone);
        LOGGER.info("current ringtone {}", uri);
        this.pickRingtoneLauncher.launch(uri);
    }

    private void setRingtone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        sharedPreferences
                .edit()
                .putString(RINGTONE_PREFERENCE_KEY, uri == null ? null : uri.toString())
                .apply();
    }
}
