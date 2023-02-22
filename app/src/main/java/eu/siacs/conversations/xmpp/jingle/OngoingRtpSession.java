package eu.siacs.conversations.xmpp.jingle;

import org.jxmpp.jid.Jid;

public interface OngoingRtpSession {
    Jid getWith();

    String getSessionId();
}
