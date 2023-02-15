package im.conversations.android.ui.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import eu.siacs.conversations.R;
import eu.siacs.conversations.SetupNavigationDirections;
import eu.siacs.conversations.databinding.ActivitySetupBinding;
import im.conversations.android.ui.Activities;
import im.conversations.android.ui.Event;
import im.conversations.android.ui.NavControllers;
import im.conversations.android.ui.model.SetupViewModel;

public class SetupActivity extends AppCompatActivity {

    private SetupViewModel setupViewModel;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySetupBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_setup);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        this.setupViewModel = viewModelProvider.get(SetupViewModel.class);
        this.setupViewModel.getRedirection().observe(this, this::onRedirectionEvent);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
    }

    private void onRedirectionEvent(final Event<SetupViewModel.Target> targetEvent) {
        if (targetEvent.isConsumable()) {
            final NavController navController = getNavController();
            final SetupViewModel.Target target = targetEvent.consume();
            switch (target) {
                case ENTER_PASSWORD:
                    navController.navigate(SetupNavigationDirections.enterPassword());
                    break;
                default:
                    throw new IllegalStateException(
                            String.format("Unable to navigate to target %s", target));
            }
        }
    }

    private NavController getNavController() {
        return NavControllers.findNavController(this, R.id.nav_host_fragment);
    }
}
