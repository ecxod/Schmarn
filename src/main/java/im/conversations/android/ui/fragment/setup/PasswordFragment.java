package im.conversations.android.ui.fragment.setup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentPasswordBinding;

public class PasswordFragment extends AbstractSetupFragment {

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        FragmentPasswordBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_password, container, false);
        binding.setSetupViewModel(setupViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        return binding.getRoot();
    }
}
