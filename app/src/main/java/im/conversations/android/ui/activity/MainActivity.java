package im.conversations.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import im.conversations.android.R;
import im.conversations.android.databinding.ActivityMainBinding;
import im.conversations.android.service.ForegroundService;
import im.conversations.android.ui.Activities;
import im.conversations.android.ui.model.MainViewModel;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForegroundService.start(this);
        final ActivityMainBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_main);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        final var mainViewModel = viewModelProvider.get(MainViewModel.class);
        mainViewModel
                .hasNoAccounts()
                .observe(
                        this,
                        hasNoAccounts -> {
                            if (Boolean.TRUE.equals(hasNoAccounts)) {
                                startActivity(new Intent(this, SetupActivity.class));
                                finish();
                            }
                        });
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
    }
}
