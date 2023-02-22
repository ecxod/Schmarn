package eu.siacs.conversations;

import android.net.Uri;

public class Config {
    public static final String LOGTAG = "conversations";
    public static final Uri HELP = Uri.parse("https://help.conversations.im");
    public static final boolean REQUIRE_RTP_VERIFICATION =
            false; // require a/v calls to be verified with OMEMO
}
