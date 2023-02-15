package im.conversations.android.ui.fragment.setup;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import im.conversations.android.ui.model.SetupViewModel;

public abstract class AbstractSetupFragment extends Fragment {

    SetupViewModel setupViewModel;

    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ViewModelProvider viewModelProvider =
                new ViewModelProvider(requireActivity(), getDefaultViewModelProviderFactory());
        this.setupViewModel = viewModelProvider.get(SetupViewModel.class);
    }
}
