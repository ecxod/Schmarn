package im.conversations.android.transformer;

import com.google.common.base.MoreObjects;
import eu.siacs.conversations.xmpp.jingle.Media;
import im.conversations.android.database.model.PartType;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.jxmpp.jid.Jid;

public class CallLogTransformation extends Transformation {

    public final Duration duration;
    public final PartType partType;

    private CallLogTransformation(
            final Instant receivedAt,
            final Jid to,
            final Jid from,
            final Jid remote,
            final String messageId,
            final String stanzaId,
            final Duration duration,
            final PartType partType) {
        super(receivedAt, to, from, remote, Message.Type.NORMAL, messageId, stanzaId, null);
        this.duration = duration;
        this.partType = partType;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("duration", duration)
                .add("partType", partType)
                .add("receivedAt", receivedAt)
                .add("to", to)
                .add("from", from)
                .add("remote", remote)
                .add("type", type)
                .add("messageId", messageId)
                .add("stanzaId", stanzaId)
                .add("occupantId", occupantId)
                .toString();
    }

    public static class Builder {

        private final Instant receivedAt;
        private final Jid to;
        private final Jid from;
        private final Jid remote;
        private final String messageId;
        private String stanzaId;
        private Duration duration;
        private PartType partType;

        public Builder(final Jid remote, final Jid to, final Jid from, final String sessionId) {
            this.receivedAt = Instant.now();
            this.remote = remote;
            this.to = to;
            this.from = from;
            this.messageId = sessionId;
        }

        public void setStanzaId(final String stanzaId) {
            this.stanzaId = stanzaId;
        }

        public void setCarbon(boolean b) {}

        public void markUnread() {}

        public void setDuration(final Duration duration) {
            this.duration = duration;
        }

        public void setIsMissedCall() {
            this.duration = Duration.ZERO;
        }

        public void setMedia(final Set<Media> media) {
            this.partType = Media.audioOnly(media) ? PartType.AUDIO_CALL : PartType.VIDEO_CALL;
        }

        public CallLogTransformation build() {
            return new CallLogTransformation(
                    receivedAt, to, from, remote, messageId, stanzaId, duration, partType);
        }
    }
}
