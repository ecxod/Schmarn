package im.conversations.android.database.model;

public abstract class AvatarBase {

    public final String id;
    public final String type;
    public final long bytes;
    public final long height;
    public final long width;

    public AvatarBase(String id, String type, long bytes, long height, long width) {
        this.id = id;
        this.type = type;
        this.bytes = bytes;
        this.height = height;
        this.width = width;
    }
}
