package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlElementReader;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class XmlElementReaderTest {

    @Test
    public void readRosterItems() throws IOException {
        final String xml =
                "<query xmlns='jabber:iq:roster'><item subscription='none' jid='a@b.c'/><item"
                        + " subscription='both' jid='d@e.f' ask='subscribe'/></query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Query.class));
        final Query query = (Query) element;
        final Collection<Item> items = query.getExtensions(Item.class);
        assertEquals(2, items.size());
    }
}
