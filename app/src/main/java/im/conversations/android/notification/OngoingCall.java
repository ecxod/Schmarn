package im.conversations.android.notification;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import java.util.Set;

public class OngoingCall {
    public final AbstractJingleConnection.Id id;
    public final Set<Media> media;
    public final boolean reconnecting;

    public OngoingCall(
            AbstractJingleConnection.Id id, Set<Media> media, final boolean reconnecting) {
        this.id = id;
        this.media = media;
        this.reconnecting = reconnecting;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OngoingCall that = (OngoingCall) o;
        return reconnecting == that.reconnecting
                && Objects.equal(id, that.id)
                && Objects.equal(media, that.media);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, media, reconnecting);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("media", media)
                .add("reconnecting", reconnecting)
                .toString();
    }
}
