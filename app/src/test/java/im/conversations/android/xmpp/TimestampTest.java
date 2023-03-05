package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import im.conversations.android.xml.Element;
import im.conversations.android.xml.XmlElementReader;
import im.conversations.android.xmpp.model.delay.Delay;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TimestampTest {

    @Test
    public void testZuluNoMillis() throws IOException {
        final String xml =
                """
                        <delay xmlns='urn:xmpp:delay'
                             from='capulet.com'
                             stamp='2002-09-10T23:08:25Z'/>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final Delay delay = (Delay) element;
        assertEquals(1031699305000L, delay.getStamp().toEpochMilli());
    }

    @Test
    public void testZuluWithMillis() throws IOException {
        final String xml =
                """
                        <delay xmlns='urn:xmpp:delay'
                             from='capulet.com'
                             stamp='2002-09-10T23:08:25.023Z'/>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(Delay.class));
        final Delay delay = (Delay) element;
        assertEquals(1031699305023L, delay.getStamp().toEpochMilli());
    }
}
