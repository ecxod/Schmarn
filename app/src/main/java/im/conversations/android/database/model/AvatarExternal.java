package im.conversations.android.database.model;

import im.conversations.android.xmpp.model.avatar.Info;

public class AvatarExternal extends AvatarBase {

    public final String url;

    public AvatarExternal(String id, String type, long bytes, long height, long width, String url) {
        super(id, type, bytes, height, width);
        this.url = url;
    }

    public static AvatarExternal of(Info info) {
        return new AvatarExternal(
                info.getId(),
                info.getType(),
                info.getBytes(),
                info.getHeight(),
                info.getWidth(),
                info.getUrl());
    }
}
