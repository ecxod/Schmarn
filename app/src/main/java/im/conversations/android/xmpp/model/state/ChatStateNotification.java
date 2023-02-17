package im.conversations.android.xmpp.model.state;

import im.conversations.android.xmpp.model.Extension;

public abstract class ChatStateNotification extends Extension {

    protected ChatStateNotification(Class<? extends Extension> clazz) {
        super(clazz);
    }
}
