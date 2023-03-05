package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import im.conversations.android.xml.Element;
import im.conversations.android.xml.XmlElementReader;
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
                """
                        <iq type='result'
                            to='juliet@capulet.lit/balcony'
                            id='retrieve1' xmlns='jabber:client'>
                          <pubsub xmlns='http://jabber.org/protocol/pubsub'>
                            <items node='urn:xmpp:bookmarks:1'>
                              <item id='theplay@conference.shakespeare.lit'>
                                <conference xmlns='urn:xmpp:bookmarks:1'
                                            name='The Play&apos;s the Thing'
                                            autojoin='true'>
                                  <nick>JC</nick>
                                </conference>
                              </item>
                              <item id='orchard@conference.shakespeare.lit'>
                                <conference xmlns='urn:xmpp:bookmarks:1'
                                            name='The Orcard'
                                            autojoin='1'>
                                  <nick>JC</nick>
                                  <extensions>
                                    <state xmlns='http://myclient.example/bookmark/state' minimized='true'/>
                                  </extensions>
                                </conference>
                              </item>
                            </items>
                          </pubsub>
                        </iq>""";
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
