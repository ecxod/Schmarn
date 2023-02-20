package im.conversations.android.ui.activity;

import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import im.conversations.android.R;
import im.conversations.android.databinding.ActivitySettingsBinding;
import im.conversations.android.service.ForegroundService;
import im.conversations.android.ui.Activities;
import im.conversations.android.ui.fragment.settings.MainSettingsFragment;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForegroundService.start(this);
        final ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        setSupportActionBar(binding.materialToolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot(), true);

        final var fragmentManager = getSupportFragmentManager();
        final var currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, new MainSettingsFragment())
                    .commit();
        }
        binding.materialToolbar.setNavigationOnClickListener(
                view -> {
                    if (fragmentManager.getBackStackEntryCount() == 0) {
                        finish();
                    } else {
                        fragmentManager.popBackStack();
                    }
                });
    }
}
