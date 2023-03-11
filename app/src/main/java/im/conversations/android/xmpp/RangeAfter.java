package im.conversations.android.xmpp;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;

public class RangeAfter extends Range {

    public final String afterId;

    public RangeAfter(final String afterId, final String id) {
        super(Order.REVERSE, id);
        this.afterId = afterId;
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("afterId", afterId)
                .add("order", order)
                .add("id", id)
                .toString();
    }
}
