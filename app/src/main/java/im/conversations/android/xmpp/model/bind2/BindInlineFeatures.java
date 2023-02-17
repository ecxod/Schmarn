package im.conversations.android.xmpp.model.bind2;

import com.google.common.collect.Collections2;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.model.sasl2.Inline;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class BindInlineFeatures {

    public static final Collection<String> QUICKSTART_FEATURES =
            Arrays.asList(Namespace.CARBONS, Namespace.STREAM_MANAGEMENT);

    public static Collection<String> get(final Inline inline) {
        final Element inlineBind2 =
                inline != null ? inline.findChild("bind", Namespace.BIND2) : null;
        final Element inlineBind2Inline =
                inlineBind2 != null ? inlineBind2.findChild("inline", Namespace.BIND2) : null;
        if (inlineBind2 == null) {
            return null;
        }
        if (inlineBind2Inline == null) {
            return Collections.emptyList();
        }
        return Collections2.transform(
                inlineBind2Inline.getChildren(), c -> c == null ? null : c.getAttribute("var"));
    }
}
