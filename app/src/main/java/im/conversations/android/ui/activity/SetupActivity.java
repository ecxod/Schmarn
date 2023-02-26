package im.conversations.android.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import im.conversations.android.R;
import im.conversations.android.SetupNavigationDirections;
import im.conversations.android.databinding.ActivitySetupBinding;
import im.conversations.android.ui.Activities;
import im.conversations.android.ui.Event;
import im.conversations.android.ui.NavControllers;
import im.conversations.android.ui.model.SetupViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupActivity extends BaseActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupActivity.class);

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
        this.setupViewModel.getGenericErrorEvent().observe(this, this::onGenericErrorEvent);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
    }

    private void onGenericErrorEvent(final Event<String> errorEvent) {
        if (errorEvent.isConsumable()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(errorEvent.consume())
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    private void onRedirectionEvent(final Event<SetupViewModel.Target> targetEvent) {
        if (targetEvent.isConsumable()) {
            final NavController navController = getNavController();
            final SetupViewModel.Target target = targetEvent.consume();
            switch (target) {
                case ENTER_PASSWORD:
                    navController.navigate(SetupNavigationDirections.enterPassword());
                    break;
                case ENTER_HOSTNAME:
                    navController.navigate(SetupNavigationDirections.enterHostname());
                    break;
                case DONE:
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
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

    @Override
    public void onBackPressed() {
        if (this.setupViewModel.cancelCurrentOperation()) {
            return;
        }
        final var navController = getNavController();
        final var destination = navController.getCurrentDestination();
        if (destination != null && destination.getId() == R.id.signIn) {
            LOGGER.info("User pressed back in signIn. Cancel setup");
            this.setupViewModel.cancelSetup();
        }
        super.onBackPressed();
    }
}
