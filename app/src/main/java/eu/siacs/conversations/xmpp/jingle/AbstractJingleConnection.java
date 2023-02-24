package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import im.conversations.android.IDs;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Iq;
import org.jxmpp.jid.Jid;

public abstract class AbstractJingleConnection extends XmppConnection.Delegate {

    public static final String JINGLE_MESSAGE_PROPOSE_ID_PREFIX = "jm-propose-";
    public static final String JINGLE_MESSAGE_PROCEED_ID_PREFIX = "jm-proceed-";

    protected final Id id;
    private final Jid initiator;

    AbstractJingleConnection(
            final Context context,
            final XmppConnection connection,
            final Id id,
            final Jid initiator) {
        super(context, connection);
        this.id = id;
        this.initiator = initiator;
    }

    boolean isInitiator() {
        return initiator.equals(connection.getBoundAddress());
    }

    public abstract void deliverPacket(Iq jinglePacket);

    public Id getId() {
        return id;
    }

    public abstract void notifyRebound();

    public static class Id implements OngoingRtpSession {
        public final Jid with;
        public final String sessionId;

        private Id(final Jid with, final String sessionId) {
            Preconditions.checkNotNull(with);
            Preconditions.checkNotNull(sessionId);
            this.with = with;
            this.sessionId = sessionId;
        }

        public static Id of(final JinglePacket jinglePacket) {
            return new Id(jinglePacket.getFrom(), jinglePacket.getSessionId());
        }

        public static Id of(Jid with, final String sessionId) {
            return new Id(with, sessionId);
        }

        public static Id of(Jid with) {
            return new Id(with, IDs.medium());
        }

        @Override
        public Jid getWith() {
            return with;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Id id = (Id) o;
            return Objects.equal(with, id.with) && Objects.equal(sessionId, id.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(with, sessionId);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("with", with)
                    .add("sessionId", sessionId)
                    .toString();
        }
    }

    public enum State {
        NULL, // default value; nothing has been sent or received yet
        PROPOSED,
        ACCEPTED,
        PROCEED,
        REJECTED,
        REJECTED_RACED, // used when we want to reject but haven’t received session init yet
        RETRACTED,
        RETRACTED_RACED, // used when receiving a retract after we already asked to proceed
        SESSION_INITIALIZED, // equal to 'PENDING'
        SESSION_INITIALIZED_PRE_APPROVED,
        SESSION_ACCEPTED, // equal to 'ACTIVE'
        TERMINATED_SUCCESS, // equal to 'ENDED' (after successful call) ui will just close
        TERMINATED_DECLINED_OR_BUSY, // equal to 'ENDED' (after other party declined the call)
        TERMINATED_CONNECTIVITY_ERROR, // equal to 'ENDED' (but after network failures; ui will
        // display retry button)
        TERMINATED_CANCEL_OR_TIMEOUT, // more or less the same as retracted; caller pressed end call
        // before session was accepted
        TERMINATED_APPLICATION_FAILURE,
        TERMINATED_SECURITY_ERROR
    }
}
