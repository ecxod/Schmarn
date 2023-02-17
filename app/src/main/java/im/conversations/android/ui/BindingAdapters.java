package im.conversations.android.ui;

import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.databinding.BindingAdapter;
import androidx.lifecycle.LiveData;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.base.Supplier;

public class BindingAdapters {

    @BindingAdapter("errorText")
    public static void setErrorText(
            final TextInputLayout textInputLayout, final LiveData<String> error) {
        textInputLayout.setError(error.getValue());
    }

    @BindingAdapter("editorAction")
    public static void setEditorAction(
            final TextInputEditText editText, final @NonNull Supplier<Boolean> callback) {
        editText.setOnEditorActionListener(
                (v, actionId, event) -> {
                    // event is null when using software keyboard
                    if (event == null || event.getAction() == KeyEvent.ACTION_UP) {
                        return Boolean.TRUE.equals(callback.get());
                    }
                    return true;
                });
    }
}
