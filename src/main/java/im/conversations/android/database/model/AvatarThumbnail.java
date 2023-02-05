package im.conversations.android.database.model;

import im.conversations.android.xmpp.model.avatar.Info;

public class AvatarThumbnail extends AvatarBase {

    public AvatarThumbnail(String id, String type, long bytes, long height, long width) {
        super(id, type, bytes, height, width);
    }

    public static AvatarThumbnail of(Info info) {
        return new AvatarThumbnail(
                info.getId(), info.getType(), info.getBytes(), info.getHeight(), info.getWidth());
    }
}
