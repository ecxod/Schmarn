package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlElementReader;
import im.conversations.android.xmpp.manager.DiscoManager;
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
                        + "    <feature var='http://jabber.org/protocol/disco#items'/>\n"
                        + "    <feature var='http://jabber.org/protocol/disco#info'/>\n"
                        + "    <feature var='http://jabber.org/protocol/muc'/>\n"
                        + "  </query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities.hash(info).encoded();
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
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", var);
    }

    @Test
    public void caps2() throws IOException {
        final String xml =
                "<query xmlns=\"http://jabber.org/protocol/disco#info\">\n"
                    + "  <identity category=\"client\" name=\"BombusMod\" type=\"mobile\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/si\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/bytestreams\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/chatstates\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/disco#info\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/disco#items\"/>\n"
                    + "  <feature var=\"urn:xmpp:ping\"/>\n"
                    + "  <feature var=\"jabber:iq:time\"/>\n"
                    + "  <feature var=\"jabber:iq:privacy\"/>\n"
                    + "  <feature var=\"jabber:iq:version\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/rosterx\"/>\n"
                    + "  <feature var=\"urn:xmpp:time\"/>\n"
                    + "  <feature var=\"jabber:x:oob\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/ibb\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/si/profile/file-transfer\"/>\n"
                    + "  <feature var=\"urn:xmpp:receipts\"/>\n"
                    + "  <feature var=\"jabber:iq:roster\"/>\n"
                    + "  <feature var=\"jabber:iq:last\"/>\n"
                    + "</query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities2.hash(info).encoded();
        Assert.assertEquals("kzBZbkqJ3ADrj7v08reD1qcWUwNGHaidNUgD7nHpiw8=", var);
    }

    @Test
    public void caps2complex() throws IOException {
        final String xml =
                "<query xmlns=\"http://jabber.org/protocol/disco#info\">\n"
                    + "  <identity category=\"client\" name=\"Tkabber\" type=\"pc\""
                    + " xml:lang=\"en\"/>\n"
                    + "  <identity category=\"client\" name=\"Ткаббер\" type=\"pc\""
                    + " xml:lang=\"ru\"/>\n"
                    + "  <feature var=\"games:board\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/activity\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/activity+notify\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/bytestreams\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/chatstates\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/commands\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/disco#info\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/disco#items\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/evil\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/feature-neg\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/geoloc\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/geoloc+notify\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/ibb\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/iqibb\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/mood\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/mood+notify\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/rosterx\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/si\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/si/profile/file-transfer\"/>\n"
                    + "  <feature var=\"http://jabber.org/protocol/tune\"/>\n"
                    + "  <feature var=\"http://www.facebook.com/xmpp/messages\"/>\n"
                    + "  <feature"
                    + " var=\"http://www.xmpp.org/extensions/xep-0084.html#ns-metadata+notify\"/>\n"
                    + "  <feature var=\"jabber:iq:avatar\"/>\n"
                    + "  <feature var=\"jabber:iq:browse\"/>\n"
                    + "  <feature var=\"jabber:iq:dtcp\"/>\n"
                    + "  <feature var=\"jabber:iq:filexfer\"/>\n"
                    + "  <feature var=\"jabber:iq:ibb\"/>\n"
                    + "  <feature var=\"jabber:iq:inband\"/>\n"
                    + "  <feature var=\"jabber:iq:jidlink\"/>\n"
                    + "  <feature var=\"jabber:iq:last\"/>\n"
                    + "  <feature var=\"jabber:iq:oob\"/>\n"
                    + "  <feature var=\"jabber:iq:privacy\"/>\n"
                    + "  <feature var=\"jabber:iq:roster\"/>\n"
                    + "  <feature var=\"jabber:iq:time\"/>\n"
                    + "  <feature var=\"jabber:iq:version\"/>\n"
                    + "  <feature var=\"jabber:x:data\"/>\n"
                    + "  <feature var=\"jabber:x:event\"/>\n"
                    + "  <feature var=\"jabber:x:oob\"/>\n"
                    + "  <feature var=\"urn:xmpp:avatar:metadata+notify\"/>\n"
                    + "  <feature var=\"urn:xmpp:ping\"/>\n"
                    + "  <feature var=\"urn:xmpp:receipts\"/>\n"
                    + "  <feature var=\"urn:xmpp:time\"/>\n"
                    + "  <x xmlns=\"jabber:x:data\" type=\"result\">\n"
                    + "    <field type=\"hidden\" var=\"FORM_TYPE\">\n"
                    + "      <value>urn:xmpp:dataforms:softwareinfo</value>\n"
                    + "    </field>\n"
                    + "    <field var=\"software\">\n"
                    + "      <value>Tkabber</value>\n"
                    + "    </field>\n"
                    + "    <field var=\"software_version\">\n"
                    + "      <value>0.11.1-svn-20111216-mod (Tcl/Tk 8.6b2)</value>\n"
                    + "    </field>\n"
                    + "    <field var=\"os\">\n"
                    + "      <value>Windows</value>\n"
                    + "    </field>\n"
                    + "    <field var=\"os_version\">\n"
                    + "      <value>XP</value>\n"
                    + "    </field>\n"
                    + "  </x>\n"
                    + "</query>";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities2.hash(info).encoded();
        Assert.assertEquals("u79ZroNJbdSWhdSp311mddz44oHHPsEBntQ5b1jqBSY=", var);
    }

    @Test
    public void parseCaps2Node() {
        final var caps =
                DiscoManager.buildHashFromNode(
                        "urn:xmpp:caps#sha-256.u79ZroNJbdSWhdSp311mddz44oHHPsEBntQ5b1jqBSY=");
        assertThat(caps, instanceOf(EntityCapabilities2.EntityCaps2Hash.class));
    }

    @Test
    public void parseCaps2NodeMissingHash() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#sha-256.");
        assertNull(caps);
    }

    @Test
    public void parseCaps2NodeInvalid() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#-");
        assertNull(caps);
    }

    @Test
    public void parseCaps2NodeUnknownAlgo() {
        final var caps = DiscoManager.buildHashFromNode("urn:xmpp:caps#test.test");
        assertNull(caps);
    }
}
