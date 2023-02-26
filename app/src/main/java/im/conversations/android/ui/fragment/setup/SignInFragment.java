package im.conversations.android.ui.fragment.setup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import im.conversations.android.R;
import im.conversations.android.databinding.FragmentSignInBinding;

public class SignInFragment extends AbstractSetupFragment {

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final FragmentSignInBinding binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_sign_in, container, false);
        binding.setSetupViewModel(setupViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());
        final var menu = binding.materialToolbar.getMenu();
        menu.findItem(R.id.scan_qr_code).setVisible(true);
        menu.findItem(R.id.certificate_login).setVisible(true);
        return binding.getRoot();
    }
}
