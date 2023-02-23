package im.conversations.android.transformer;

import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Duration;
import java.time.Instant;
import org.jxmpp.jid.Jid;

public class CallLogTransformation extends Transformation {

    public final Duration duration;

    private CallLogTransformation(
            final Instant receivedAt,
            final Jid to,
            final Jid from,
            final Jid remote,
            final String messageId,
            final String stanzaId,
            final Duration duration) {
        super(receivedAt, to, from, remote, Message.Type.NORMAL, messageId, stanzaId, null);
        this.duration = duration;
    }

    public static class Builder {

        public void setServerMsgId(String serverMsgId) {}

        public void setCarbon(boolean b) {}

        public void markUnread() {}

        public void setDuration(long duration) {}

        public CallLogTransformation build() {
            return new CallLogTransformation(null, null, null, null, null, null, null);
        }
    }
}
