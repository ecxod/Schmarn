package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import im.conversations.android.xml.Element;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import im.conversations.android.xmpp.model.stanza.Message;
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

    public void readMessageError() throws IOException {
        final String xml =
                """
                        <message
                            to='juliet@capulet.com/balcony'
                            from='romeo@montague.net/garden'
                            xmlns='jabber:client'
                            type='error'>
                          <body>Wherefore art thou, Romeo?</body>
                          <error code='404' type='cancel'>
                            <item-not-found xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
                          </error>
                        </message>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Message.class));
        final Message message = (Message) element;
        final Error error = message.getError();
        assertThat(error.getCondition(), instanceOf(Condition.ItemNotFound.class));
    }
}
