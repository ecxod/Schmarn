package im.conversations.android.xmpp;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;

import im.conversations.android.xml.Element;
import im.conversations.android.xml.XmlElementReader;
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
                """
                        <query xmlns='http://jabber.org/protocol/disco#info'
                                 node='http://code.google.com/p/exodus#QgayPKawpkPSDYmwT/WM94uAlu0='>
                            <identity category='client' name='Exodus 0.9.1' type='pc'/>
                            <feature var='http://jabber.org/protocol/caps'/>
                            <feature var='http://jabber.org/protocol/disco#items'/>
                            <feature var='http://jabber.org/protocol/disco#info'/>
                            <feature var='http://jabber.org/protocol/muc'/>
                          </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("QgayPKawpkPSDYmwT/WM94uAlu0=", var);
    }

    @Test
    public void entityCapsComplexExample() throws IOException {
        final String xml =
                """
                        <query xmlns='http://jabber.org/protocol/disco#info'
                                 node='http://psi-im.org#q07IKJEyjvHSyhy//CH0CxmKi8w='>
                            <identity xml:lang='en' category='client' name='Psi 0.11' type='pc'/>
                            <identity xml:lang='el' category='client' name='Ψ 0.11' type='pc'/>
                            <feature var='http://jabber.org/protocol/caps'/>
                            <feature var='http://jabber.org/protocol/disco#info'/>
                            <feature var='http://jabber.org/protocol/disco#items'/>
                            <feature var='http://jabber.org/protocol/muc'/>
                            <x xmlns='jabber:x:data' type='result'>
                              <field var='FORM_TYPE' type='hidden'>
                                <value>urn:xmpp:dataforms:softwareinfo</value>
                              </field>
                              <field var='ip_version' type='text-multi' >
                                <value>ipv4</value>
                                <value>ipv6</value>
                              </field>
                              <field var='os'>
                                <value>Mac</value>
                              </field>
                              <field var='os_version'>
                                <value>10.5.1</value>
                              </field>
                              <field var='software'>
                                <value>Psi</value>
                              </field>
                              <field var='software_version'>
                                <value>0.11</value>
                              </field>
                            </x>
                          </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities.hash(info).encoded();
        Assert.assertEquals("q07IKJEyjvHSyhy//CH0CxmKi8w=", var);
    }

    @Test
    public void caps2() throws IOException {
        final String xml =
                """
                        <query xmlns="http://jabber.org/protocol/disco#info">
                          <identity category="client" name="BombusMod" type="mobile"/>
                          <feature var="http://jabber.org/protocol/si"/>
                          <feature var="http://jabber.org/protocol/bytestreams"/>
                          <feature var="http://jabber.org/protocol/chatstates"/>
                          <feature var="http://jabber.org/protocol/disco#info"/>
                          <feature var="http://jabber.org/protocol/disco#items"/>
                          <feature var="urn:xmpp:ping"/>
                          <feature var="jabber:iq:time"/>
                          <feature var="jabber:iq:privacy"/>
                          <feature var="jabber:iq:version"/>
                          <feature var="http://jabber.org/protocol/rosterx"/>
                          <feature var="urn:xmpp:time"/>
                          <feature var="jabber:x:oob"/>
                          <feature var="http://jabber.org/protocol/ibb"/>
                          <feature var="http://jabber.org/protocol/si/profile/file-transfer"/>
                          <feature var="urn:xmpp:receipts"/>
                          <feature var="jabber:iq:roster"/>
                          <feature var="jabber:iq:last"/>
                        </query>""";
        final Element element = XmlElementReader.read(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(element, instanceOf(InfoQuery.class));
        final InfoQuery info = (InfoQuery) element;
        final String var = EntityCapabilities2.hash(info).encoded();
        Assert.assertEquals("kzBZbkqJ3ADrj7v08reD1qcWUwNGHaidNUgD7nHpiw8=", var);
    }

    @Test
    public void caps2complex() throws IOException {
        final String xml =
                """
                        <query xmlns="http://jabber.org/protocol/disco#info">
                          <identity category="client" name="Tkabber" type="pc" xml:lang="en"/>
                          <identity category="client" name="Ткаббер" type="pc" xml:lang="ru"/>
                          <feature var="games:board"/>
                          <feature var="http://jabber.org/protocol/activity"/>
                          <feature var="http://jabber.org/protocol/activity+notify"/>
                          <feature var="http://jabber.org/protocol/bytestreams"/>
                          <feature var="http://jabber.org/protocol/chatstates"/>
                          <feature var="http://jabber.org/protocol/commands"/>
                          <feature var="http://jabber.org/protocol/disco#info"/>
                          <feature var="http://jabber.org/protocol/disco#items"/>
                          <feature var="http://jabber.org/protocol/evil"/>
                          <feature var="http://jabber.org/protocol/feature-neg"/>
                          <feature var="http://jabber.org/protocol/geoloc"/>
                          <feature var="http://jabber.org/protocol/geoloc+notify"/>
                          <feature var="http://jabber.org/protocol/ibb"/>
                          <feature var="http://jabber.org/protocol/iqibb"/>
                          <feature var="http://jabber.org/protocol/mood"/>
                          <feature var="http://jabber.org/protocol/mood+notify"/>
                          <feature var="http://jabber.org/protocol/rosterx"/>
                          <feature var="http://jabber.org/protocol/si"/>
                          <feature var="http://jabber.org/protocol/si/profile/file-transfer"/>
                          <feature var="http://jabber.org/protocol/tune"/>
                          <feature var="http://www.facebook.com/xmpp/messages"/>
                          <feature var="http://www.xmpp.org/extensions/xep-0084.html#ns-metadata+notify"/>
                          <feature var="jabber:iq:avatar"/>
                          <feature var="jabber:iq:browse"/>
                          <feature var="jabber:iq:dtcp"/>
                          <feature var="jabber:iq:filexfer"/>
                          <feature var="jabber:iq:ibb"/>
                          <feature var="jabber:iq:inband"/>
                          <feature var="jabber:iq:jidlink"/>
                          <feature var="jabber:iq:last"/>
                          <feature var="jabber:iq:oob"/>
                          <feature var="jabber:iq:privacy"/>
                          <feature var="jabber:iq:roster"/>
                          <feature var="jabber:iq:time"/>
                          <feature var="jabber:iq:version"/>
                          <feature var="jabber:x:data"/>
                          <feature var="jabber:x:event"/>
                          <feature var="jabber:x:oob"/>
                          <feature var="urn:xmpp:avatar:metadata+notify"/>
                          <feature var="urn:xmpp:ping"/>
                          <feature var="urn:xmpp:receipts"/>
                          <feature var="urn:xmpp:time"/>
                          <x xmlns="jabber:x:data" type="result">
                            <field type="hidden" var="FORM_TYPE">
                              <value>urn:xmpp:dataforms:softwareinfo</value>
                            </field>
                            <field var="software">
                              <value>Tkabber</value>
                            </field>
                            <field var="software_version">
                              <value>0.11.1-svn-20111216-mod (Tcl/Tk 8.6b2)</value>
                            </field>
                            <field var="os">
                              <value>Windows</value>
                            </field>
                            <field var="os_version">
                              <value>XP</value>
                            </field>
                          </x>
                        </query>""";
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
