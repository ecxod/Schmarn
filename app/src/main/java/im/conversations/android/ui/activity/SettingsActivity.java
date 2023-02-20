package im.conversations.android.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import im.conversations.android.R;
import im.conversations.android.databinding.ActivitySettingsBinding;
import im.conversations.android.service.ForegroundService;
import im.conversations.android.ui.Activities;
import im.conversations.android.ui.fragment.settings.MainSettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForegroundService.start(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_settings);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new MainSettingsFragment())
                .commit();
    }
}
