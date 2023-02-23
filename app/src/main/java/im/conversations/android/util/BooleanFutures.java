package im.conversations.android.util;

import com.google.common.util.concurrent.ListenableFuture;

public final class BooleanFutures {

    private BooleanFutures() {}

    public static boolean isDoneAndTrue(final ListenableFuture<Boolean> future) {
        if (future.isDone()) {
            try {
                return Boolean.TRUE.equals(future.get());
            } catch (final Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }
}
