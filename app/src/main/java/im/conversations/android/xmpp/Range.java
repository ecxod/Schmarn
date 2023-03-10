package im.conversations.android.xmpp;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;

public class Range {

    public final Order order;
    public final String id;

    public Range(final Order order, final String id) {
        this.order = order;
        this.id = id;
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("order", order).add("id", id).toString();
    }

    public enum Order {
        NORMAL,
        REVERSE
    }
}
