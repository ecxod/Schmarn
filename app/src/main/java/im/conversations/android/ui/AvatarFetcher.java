package im.conversations.android.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.database.model.AddressWithName;
import im.conversations.android.database.model.AvatarWithAccount;
import im.conversations.android.ui.graphics.drawable.AvatarDrawable;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.manager.AvatarManager;
import java.lang.ref.WeakReference;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvatarFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarFetcher.class);

    public final AvatarWithAccount avatar;
    public final ListenableFuture<Bitmap> future;

    private AvatarFetcher(
            AvatarWithAccount avatar, ListenableFuture<Bitmap> future, ImageView imageView) {
        this.avatar = avatar;
        this.future = future;
        Futures.addCallback(
                this.future,
                new Callback(imageView, avatar.addressWithName),
                ContextCompat.getMainExecutor(imageView.getContext()));
    }

    public static void setDefault(
            final ImageView imageView, final AddressWithName addressWithName) {
        imageView.setImageDrawable(new AvatarDrawable(imageView.getContext(), addressWithName));
    }

    private static class Callback implements FutureCallback<Bitmap> {

        private final WeakReference<ImageView> imageViewWeakReference;
        private final AddressWithName addressWithName;

        private Callback(final ImageView imageView, final AddressWithName addressWithName) {
            this.imageViewWeakReference = new WeakReference<>(imageView);
            this.addressWithName = addressWithName;
        }

        @Override
        public void onSuccess(final Bitmap result) {
            final var imageView = imageViewWeakReference.get();
            if (imageView == null) {
                LOGGER.info("ImageView reference was gone after fetching avatar");
                return;
            }
            final var roundedBitmapDrawable =
                    RoundedBitmapDrawableFactory.create(imageView.getResources(), result);
            roundedBitmapDrawable.setCircular(true);
            imageView.setImageDrawable(roundedBitmapDrawable);
        }

        @Override
        public void onFailure(@NonNull final Throwable throwable) {
            if (throwable instanceof CancellationException) {
                return;
            }
            // TODO on IqTimeout remove tag?
            final var imageView = imageViewWeakReference.get();
            if (imageView == null) {
                LOGGER.info("ImageView reference was gone after avatar fetch failed");
                return;
            }
            LOGGER.info("Could not load avatar", throwable);
            setDefault(imageView, this.addressWithName);
        }
    }

    public static void fetchInto(final ImageView imageView, final AvatarWithAccount avatar) {
        final var tag = imageView.getTag();
        if (tag instanceof AvatarFetcher avatarFetcher) {
            if (avatar.equals(avatarFetcher.avatar) && !avatarFetcher.future.isCancelled()) {
                if (avatarFetcher.future.isDone()) {
                    Futures.addCallback(
                            avatarFetcher.future,
                            new Callback(imageView, avatar.addressWithName),
                            ContextCompat.getMainExecutor(imageView.getContext()));
                }
                return;
            }
            avatarFetcher.future.cancel(true);
        }
        final var future = getAvatar(imageView.getContext(), avatar);
        // set default avatar until proper avatar is loaded
        setDefault(imageView, avatar.addressWithName);
        final var avatarFetcher = new AvatarFetcher(avatar, future, imageView);
        imageView.setTag(avatarFetcher);
    }

    private static ListenableFuture<Bitmap> getAvatar(
            final Context context, final AvatarWithAccount avatar) {
        return Futures.transformAsync(
                getAvatarManager(context, avatar.account),
                am -> {
                    return am.getAvatarBitmap(
                            avatar.addressWithName.address, avatar.avatarType, avatar.hash);
                },
                MoreExecutors.directExecutor());
    }

    private static ListenableFuture<AvatarManager> getAvatarManager(
            final Context context, final long account) {
        return Futures.transform(
                ConnectionPool.getInstance(context).get(account),
                xc -> xc.getManager(AvatarManager.class),
                MoreExecutors.directExecutor());
    }
}
