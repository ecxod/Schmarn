package im.conversations.android.xmpp.model.jmi;

import com.google.common.collect.ImmutableList;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import java.util.List;

public class Propose extends JingleMessage {

    public Propose() {
        super(Propose.class);
    }

    public List<GenericDescription> getDescriptions() {
        final ImmutableList.Builder<GenericDescription> builder = new ImmutableList.Builder<>();
        for (final Element child : this.children) {
            if ("description".equals(child.getName())) {
                final String namespace = child.getNamespace();
                if (FileTransferDescription.NAMESPACES.contains(namespace)) {
                    builder.add(FileTransferDescription.upgrade(child));
                } else if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                    builder.add(RtpDescription.upgrade(child));
                } else {
                    builder.add(GenericDescription.upgrade(child));
                }
            }
        }
        return builder.build();
    }
}
