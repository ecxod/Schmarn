package im.conversations.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import com.google.common.base.Strings;

public class AppSettings {

    public static final String PREFERENCE_KEY_RINGTONE = "call_ringtone";
    public static final String PREFERENCE_KEY_BTBV = "btbv";

    private final Context context;

    public AppSettings(final Context context) {
        this.context = context;
    }

    public Uri getRingtone() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String incomingCallRingtone =
                sharedPreferences.getString(
                        PREFERENCE_KEY_RINGTONE,
                        context.getString(R.string.incoming_call_ringtone));
        return Strings.isNullOrEmpty(incomingCallRingtone) ? null : Uri.parse(incomingCallRingtone);
    }

    public void setRingtone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences
                .edit()
                .putString(PREFERENCE_KEY_RINGTONE, uri == null ? null : uri.toString())
                .apply();
    }

    public boolean isBtbv() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(
                PREFERENCE_KEY_BTBV, context.getResources().getBoolean(R.bool.btbv));
    }
}
