package im.conversations.android.xml;

public final class Entities {
    private Entities() {}

    public static String encode(final String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "");
    }
}
