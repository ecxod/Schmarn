package im.conversations.android.ui.fragment.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.elevation.SurfaceColors;
import com.google.android.material.search.SearchView;
import im.conversations.android.R;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.ChatFilter;
import im.conversations.android.database.model.GroupIdentifier;
import im.conversations.android.databinding.FragmentOverviewBinding;
import im.conversations.android.ui.Intents;
import im.conversations.android.ui.activity.SettingsActivity;
import im.conversations.android.ui.activity.SetupActivity;
import im.conversations.android.ui.model.OverviewViewModel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverviewFragment extends Fragment {

    private static final Logger LOGGER = LoggerFactory.getLogger(OverviewViewModel.class);

    private FragmentOverviewBinding binding;

    private OverviewViewModel overviewViewModel;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        this.binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_overview, container, false);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(this, getDefaultViewModelProviderFactory());
        this.overviewViewModel = viewModelProvider.get(OverviewViewModel.class);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        binding.searchBar.setNavigationOnClickListener(view -> binding.drawerLayout.open());
        binding.searchView.addTransitionListener(
                (searchView, previousState, newState) -> {
                    final var activity = requireActivity();
                    final var window = activity.getWindow();
                    if (newState == SearchView.TransitionState.SHOWN) {
                        window.setStatusBarColor(SurfaceColors.SURFACE_4.getColor(activity));
                    } else if (newState == SearchView.TransitionState.SHOWING
                            || newState == SearchView.TransitionState.HIDING) {
                        window.setStatusBarColor(SurfaceColors.SURFACE_1.getColor(activity));
                    } else {
                        window.setStatusBarColor(SurfaceColors.SURFACE_0.getColor(activity));
                    }
                });
        binding.navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        binding.navigationView.setCheckedItem(R.id.chats);
        this.overviewViewModel
                .getAccounts()
                .observe(getViewLifecycleOwner(), this::onAccountsUpdated);
        this.overviewViewModel.getGroups().observe(getViewLifecycleOwner(), this::onGroupsUpdated);
        this.overviewViewModel
                .getChatFilterAvailable()
                .observe(getViewLifecycleOwner(), this::onChatFilterAvailable);
        return binding.getRoot();
    }

    private boolean onNavigationItemSelected(final MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.chats) {
            setChatFilter(null);
            return true;
        }
        if (menuItem.getItemId() == R.id.add_account) {
            return startActivity(SetupActivity.class);
        }
        if (menuItem.getItemId() == R.id.settings) {
            return startActivity(SettingsActivity.class);
        }
        final var intent = menuItem.getIntent();
        if (intent == null) {
            return false;
        }
        setChatFilter(Intents.toChatFilter(intent));
        return true;
    }

    private boolean startActivity(final Class<? extends AppCompatActivity> activityClazz) {
        startActivity(new Intent(requireContext(), activityClazz));
        binding.drawerLayout.close();
        return false;
    }

    private void setChatFilter(final ChatFilter chatFilter) {
        overviewViewModel.setChatFilter(chatFilter);
        binding.drawerLayout.close();
    }

    private void onChatFilterAvailable(final Boolean available) {
        final var menu = this.binding.navigationView.getMenu();
        final var chatsMenuItem = menu.findItem(R.id.chats);
        if (Boolean.TRUE.equals(available)) {
            chatsMenuItem.setTitle(R.string.all_chats);
        } else {
            chatsMenuItem.setTitle(R.string.chats);
        }
    }

    private void onGroupsUpdated(final List<GroupIdentifier> groups) {
        final var menu = this.binding.navigationView.getMenu();
        final var menuItemSpaces = menu.findItem(R.id.spaces);
        if (groups.isEmpty()) {
            menuItemSpaces.setVisible(false);
            return;
        }
        menuItemSpaces.setVisible(true);
        final var subMenu = menuItemSpaces.getSubMenu();
        subMenu.clear();
        for (final GroupIdentifier group : groups) {
            final var menuItemSpace = subMenu.add(group.name);
            menuItemSpace.setCheckable(true);
            menuItemSpace.setIcon(R.drawable.ic_workspaces_24dp);
            menuItemSpace.setIntent(Intents.of(group));
        }
    }

    private void onAccountsUpdated(List<AccountIdentifier> accounts) {
        final var menu = this.binding.navigationView.getMenu();
        final var menuItemAccounts = menu.findItem(R.id.accounts);
        if (accounts.size() <= 1) {
            menuItemAccounts.setVisible(false);
            return;
        }
        menuItemAccounts.setVisible(true);
        final var subMenu = menuItemAccounts.getSubMenu();
        subMenu.clear();
        for (final AccountIdentifier account : accounts) {
            final var menuItemAccount = subMenu.add(account.address);
            menuItemAccount.setCheckable(true);
            menuItemAccount.setIcon(R.drawable.ic_person_24dp);
            menuItemAccount.setIntent(Intents.of(account));
        }
    }
}
