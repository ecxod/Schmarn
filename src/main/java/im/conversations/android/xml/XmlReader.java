package im.conversations.android.xml;

import android.util.Xml;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.Extension;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class XmlReader implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(XmlReader.class);
    private final XmlPullParser parser;
    private InputStream is;

    public XmlReader() {
        this.parser = Xml.newPullParser();
        try {
            this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (final XmlPullParserException e) {
            LOGGER.error("error setting namespace feature on parser", e);
        }
    }

    public void setInputStream(final InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IOException();
        }
        this.is = inputStream;
        try {
            this.parser.setInput(new InputStreamReader(this.is));
        } catch (final XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    public void reset() throws IOException {
        if (this.is == null) {
            throw new IOException();
        }
        try {
            parser.setInput(new InputStreamReader(this.is));
        } catch (XmlPullParserException e) {
            throw new IOException("error resetting parser");
        }
    }

    @Override
    public void close() {
        this.is = null;
    }

    public Tag readTag() throws IOException {
        try {
            while (this.is != null && parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    Tag tag = Tag.start(parser.getName());
                    final String xmlns = parser.getNamespace();
                    for (int i = 0; i < parser.getAttributeCount(); ++i) {
                        final String prefix = parser.getAttributePrefix(i);
                        String name;
                        if (prefix != null && !prefix.isEmpty()) {
                            name = prefix + ":" + parser.getAttributeName(i);
                        } else {
                            name = parser.getAttributeName(i);
                        }
                        tag.setAttribute(name, parser.getAttributeValue(i));
                    }
                    if (xmlns != null) {
                        tag.setAttribute("xmlns", xmlns);
                    }
                    return tag;
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    return Tag.end(parser.getName());
                } else if (parser.getEventType() == XmlPullParser.TEXT) {
                    return Tag.no(parser.getText());
                }
            }

        } catch (Throwable throwable) {
            throw new IOException(
                    "xml parser mishandled "
                            + throwable.getClass().getSimpleName()
                            + "("
                            + throwable.getMessage()
                            + ")",
                    throwable);
        }
        return null;
    }

    public <T extends Extension> T readElement(final Tag current, Class<T> clazz)
            throws IOException {
        final Element element = readElement(current);
        if (clazz.isInstance(element)) {
            return clazz.cast(element);
        }
        throw new IOException(
                String.format("Read unexpected {%s}%s", element.getNamespace(), element.getName()));
    }

    public Element readElement(Tag currentTag) throws IOException {
        final var attributes = currentTag.getAttributes();
        final var namespace = attributes.get("xmlns");
        final var name = currentTag.getName();
        final Element element = ExtensionFactory.create(name, namespace);
        element.setAttributes(currentTag.getAttributes());
        Tag nextTag = this.readTag();
        if (nextTag == null) {
            throw new IOException("interrupted mid tag");
        }
        if (nextTag.isNo()) {
            element.setContent(nextTag.getName());
            nextTag = this.readTag();
            if (nextTag == null) {
                throw new IOException("interrupted mid tag");
            }
        }
        while (!nextTag.isEnd(element.getName())) {
            if (!nextTag.isNo()) {
                Element child = this.readElement(nextTag);
                element.addChild(child);
            }
            nextTag = this.readTag();
            if (nextTag == null) {
                throw new IOException("interrupted mid tag");
            }
        }
        return element;
    }
}
