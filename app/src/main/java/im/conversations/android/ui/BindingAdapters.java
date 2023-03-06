package im.conversations.android.ui;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.databinding.BindingAdapter;
import androidx.lifecycle.LiveData;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.base.Supplier;
import im.conversations.android.R;
import im.conversations.android.database.model.ChatOverviewItem;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class BindingAdapters {

    private static final Duration SIX_HOURS = Duration.ofHours(6);
    private static final Duration THREE_MONTH = Duration.ofDays(90);

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

    private static boolean sameYear(final Instant a, final Instant b) {
        final ZoneId local = ZoneId.systemDefault();
        return LocalDateTime.ofInstant(a, local).getYear()
                == LocalDateTime.ofInstant(b, local).getYear();
    }

    private static boolean sameDay(final Instant a, final Instant b) {
        return a.truncatedTo(ChronoUnit.DAYS).equals(b.truncatedTo(ChronoUnit.DAYS));
    }

    @BindingAdapter("instant")
    public static void setInstant(final TextView textView, final Instant instant) {
        if (instant == null || instant.getEpochSecond() <= 0) {
            textView.setVisibility(View.GONE);
        } else {
            final Context context = textView.getContext();
            final Instant now = Instant.now();
            textView.setVisibility(View.VISIBLE);
            if (sameDay(instant, now) || now.minus(SIX_HOURS).isBefore(instant)) {
                textView.setText(
                        DateUtils.formatDateTime(
                                context,
                                instant.getEpochSecond() * 1000,
                                DateUtils.FORMAT_SHOW_TIME));
            } else if (sameYear(instant, now) || now.minus(THREE_MONTH).isBefore(instant)) {
                textView.setText(
                        DateUtils.formatDateTime(
                                context,
                                instant.getEpochSecond() * 1000,
                                DateUtils.FORMAT_SHOW_DATE
                                        | DateUtils.FORMAT_NO_YEAR
                                        | DateUtils.FORMAT_ABBREV_ALL));
            } else {
                textView.setText(
                        DateUtils.formatDateTime(
                                context,
                                instant.getEpochSecond() * 1000,
                                DateUtils.FORMAT_SHOW_DATE
                                        | DateUtils.FORMAT_NO_MONTH_DAY
                                        | DateUtils.FORMAT_ABBREV_ALL));
            }
        }
    }

    @BindingAdapter("android:text")
    public static void setSender(final TextView textView, final ChatOverviewItem.Sender sender) {
        if (sender == null) {
            textView.setVisibility(View.GONE);
        } else {
            if (sender instanceof ChatOverviewItem.SenderYou) {
                textView.setText(
                        String.format("%s:", textView.getContext().getString(R.string.you)));
            } else if (sender instanceof ChatOverviewItem.SenderName senderName) {
                textView.setText(String.format("%s:", senderName.name));
            }
            textView.setVisibility(View.VISIBLE);
        }
    }
}
