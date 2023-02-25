package im.conversations.android.database.model;

public class MessageContent {

    public final String language;

    public final PartType type;

    public final String body;

    public final String url;

    public MessageContent(String language, PartType type, String body, String url) {
        this.language = language;
        this.type = type;
        this.body = body;
        this.url = url;
    }

    public static MessageContent text(final String body, final String language) {
        return new MessageContent(language, PartType.TEXT, body, null);
    }

    public static MessageContent file(final String url) {
        return new MessageContent(null, PartType.FILE, null, url);
    }
}
