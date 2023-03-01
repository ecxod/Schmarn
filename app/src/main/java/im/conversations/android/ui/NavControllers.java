package im.conversations.android.ui;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

public final class NavControllers {

    private NavControllers() {}

    public static NavController findNavController(
            final FragmentActivity activity, @IdRes int fragmentId) {
        final FragmentManager fragmentManager = activity.getSupportFragmentManager();
        final Fragment fragment = fragmentManager.findFragmentById(fragmentId);
        if (fragment instanceof NavHostFragment) {
            return ((NavHostFragment) fragment).getNavController();
        }
        throw new IllegalStateException("Fragment was not of type NavHostFragment");
    }
}
