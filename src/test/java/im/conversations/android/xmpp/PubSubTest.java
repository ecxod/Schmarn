package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlElementReader;
import im.conversations.android.xmpp.model.bookmark.Conference;
import im.conversations.android.xmpp.model.pubsub.PubSub;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PubSubTest {

    @Test
    public void parseBookmarkResult() throws IOException {
        final String xml =
                "<iq type='result'\n"
                        + "    to='juliet@capulet.lit/balcony'\n"
                        + "    id='retrieve1' xmlns='jabber:client'>\n"
                        + "  <pubsub xmlns='http://jabber.org/protocol/pubsub'>\n"
                        + "    <items node='urn:xmpp:bookmarks:1'>\n"
                        + "      <item id='theplay@conference.shakespeare.lit'>\n"
                        + "        <conference xmlns='urn:xmpp:bookmarks:1'\n"
                        + "                    name='The Play&apos;s the Thing'\n"
                        + "                    autojoin='true'>\n"
                        + "          <nick>JC</nick>\n"
                        + "        </conference>\n"
                        + "      </item>\n"
                        + "      <item id='orchard@conference.shakespeare.lit'>\n"
                        + "        <conference xmlns='urn:xmpp:bookmarks:1'\n"
                        + "                    name='The Orcard'\n"
                        + "                    autojoin='1'>\n"
                        + "          <nick>JC</nick>\n"
                        + "          <extensions>\n"
                        + "            <state xmlns='http://myclient.example/bookmark/state'"
                        + " minimized='true'/>\n"
                        + "          </extensions>\n"
                        + "        </conference>\n"
                        + "      </item>\n"
                        + "    </items>\n"
                        + "  </pubsub>\n"
                        + "</iq>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Iq.class));
        final Iq iq = (Iq) element;
        final var pubSub = iq.getExtension(PubSub.class);
        Assert.assertNotNull(pubSub);
        final var items = pubSub.getItems();
        Assert.assertNotNull(items);
        final var itemMap = items.getItemMap(Conference.class);
        Assert.assertEquals(2, itemMap.size());
        final var conference = itemMap.get("orchard@conference.shakespeare.lit");
        Assert.assertNotNull(conference);
        Assert.assertEquals("The Orcard", conference.getConferenceName());
    }
}
