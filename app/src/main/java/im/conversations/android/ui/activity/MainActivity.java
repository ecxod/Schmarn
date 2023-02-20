package im.conversations.android.ui.activity;

import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import im.conversations.android.R;
import im.conversations.android.databinding.ActivityMainBinding;
import im.conversations.android.service.ForegroundService;
import im.conversations.android.ui.Activities;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForegroundService.start(this);
        final ActivityMainBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_main);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
    }
}
