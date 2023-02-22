package eu.siacs.conversations.generator;

import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.manager.JingleConnectionManager;
import im.conversations.android.xmpp.model.stanza.Message;
import org.jxmpp.jid.Jid;

public final class MessageGenerator {

    private MessageGenerator() {
        throw new IllegalStateException("Do not instantiate me");
    }

    public static Message sessionProposal(
            final JingleConnectionManager.RtpSessionProposal proposal) {
        final Message packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(proposal.with);
        packet.setId(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX + proposal.sessionId);
        final Element propose = packet.addChild("propose", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", proposal.sessionId);
        for (final Media media : proposal.media) {
            propose.addChild("description", Namespace.JINGLE_APPS_RTP)
                    .setAttribute("media", media.toString());
        }

        packet.addChild("request", "urn:xmpp:receipts");
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public static Message sessionRetract(
            final JingleConnectionManager.RtpSessionProposal proposal) {
        final Message packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(proposal.with);
        final Element propose = packet.addChild("retract", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", proposal.sessionId);
        propose.addChild("description", Namespace.JINGLE_APPS_RTP);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public static Message sessionReject(final Jid with, final String sessionId) {
        final Message packet = new Message(Message.Type.CHAT); // we want to carbon copy those
        packet.setTo(with);
        final Element propose = packet.addChild("reject", Namespace.JINGLE_MESSAGE);
        propose.setAttribute("id", sessionId);
        propose.addChild("description", Namespace.JINGLE_APPS_RTP);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }
}
