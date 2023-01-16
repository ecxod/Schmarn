package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlElementReader;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EntityCapabilitiesTest {

    @Test
    public void entityCaps() throws IOException {
        final String xml =
                "<query xmlns='http://jabber.org/protocol/disco#info'\n"
                        + "        "
                        + " node='http://code.google.com/p/exodus#QgayPKawpkPSDYmwT/WM94uAlu0='>\n"
                        + "    <identity category='client' name='Exodus 0.9.1' type='pc'/>\n"
                        + "    <feature var='http://jabber.org/protocol/caps'/>\n"
                        + "    <feature var='http://jabber.org/protocol/disco#info'/>\n"
                        + "    <feature var='http://jabber.org/protocol/disco#items'/>\n"
                        + "    <feature var='http://jabber.org/protocol/muc'/>\n"
                        + "  </query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = BaseEncoding.base64().encode(EntityCapabilities.hash(info));
        Assert.assertEquals("QgayPKawpkPSDYmwT/WM94uAlu0=", var);
    }

    @Test
    public void entityCapsComplexExample() throws IOException {
        final String xml =
                "<query xmlns='http://jabber.org/protocol/disco#info'\n"
                    + "         node='http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w='>\n"
                    + "    <identity xml:lang='en' category='client' name='Psi 0.11' type='pc'/>\n"
                    + "    <identity xml:lang='el' category='client' name='Ψ 0.11' type='pc'/>\n"
                    + "    <feature var='http://jabber.org/protocol/caps'/>\n"
                    + "    <feature var='http://jabber.org/protocol/disco#info'/>\n"
                    + "    <feature var='http://jabber.org/protocol/disco#items'/>\n"
                    + "    <feature var='http://jabber.org/protocol/muc'/>\n"
                    + "    <x xmlns='jabber:x:data' type='result'>\n"
                    + "      <field var='FORM_TYPE' type='hidden'>\n"
                    + "        <value>urn:xmpp:dataforms:softwareinfo</value>\n"
                    + "      </field>\n"
                    + "      <field var='ip_version' type='text-multi' >\n"
                    + "        <value>ipv4</value>\n"
                    + "        <value>ipv6</value>\n"
                    + "      </field>\n"
                    + "      <field var='os'>\n"
                    + "        <value>Mac</value>\n"
                    + "      </field>\n"
                    + "      <field var='os_version'>\n"
                    + "        <value>10.5.1</value>\n"
                    + "      </field>\n"
                    + "      <field var='software'>\n"
                    + "        <value>Psi</value>\n"
                    + "      </field>\n"
                    + "      <field var='software_version'>\n"
                    + "        <value>0.11</value>\n"
                    + "      </field>\n"
                    + "    </x>\n"
                    + "  </query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = BaseEncoding.base64().encode(EntityCapabilities.hash(info));
        Assert.assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", var);
    }
}
