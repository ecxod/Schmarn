package im.conversations.android.xmpp.model.muc.user;

import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement(name = "x")
public class MultiUserChat extends Extension {

    public MultiUserChat() {
        super(MultiUserChat.class);
    }

    public Item getItem() {
        return this.getExtension(Item.class);
    }

    public Collection<Integer> getStatus() {
        return Collections2.filter(
                Collections2.transform(getExtensions(Status.class), Status::getCode),
                Objects::nonNull);
    }
}
