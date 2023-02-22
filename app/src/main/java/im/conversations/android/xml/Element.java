package im.conversations.android.xml;

import androidx.annotation.NonNull;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.model.Extension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

public class Element {
    private final String name;
    private Hashtable<String, String> attributes = new Hashtable<>();
    private String content;
    protected List<Element> children = new ArrayList<>();

    public Element(String name) {
        this.name = name;
    }

    public Element(String name, String xmlns) {
        this.name = name;
        this.setAttribute("xmlns", xmlns);
    }

    public Element addChild(Element child) {
        this.content = null;
        children.add(child);
        return child;
    }

    public <T extends Extension> T addExtension(T child) {
        this.addChild(child);
        return child;
    }

    public void addExtensions(final Collection<? extends Extension> extensions) {
        for (final Extension extension : extensions) {
            addExtension(extension);
        }
    }

    public Element addChild(String name) {
        this.content = null;
        Element child = new Element(name);
        children.add(child);
        return child;
    }

    public Element addChild(String name, String xmlns) {
        this.content = null;
        Element child = new Element(name);
        child.setAttribute("xmlns", xmlns);
        children.add(child);
        return child;
    }

    public Element setContent(String content) {
        this.content = content;
        this.children.clear();
        return this;
    }

    public Element findChild(String name) {
        for (Element child : this.children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public <E extends Extension> boolean hasExtension(final Class<E> clazz) {
        return Iterables.any(this.children, clazz::isInstance);
    }

    public <E extends Extension> E getExtension(final Class<E> clazz) {
        final var extension = Iterables.find(this.children, clazz::isInstance, null);
        if (extension == null) {
            return null;
        }
        return clazz.cast(extension);
    }

    public <E extends Extension> Collection<E> getExtensions(final Class<E> clazz) {
        return Collections2.transform(
                Collections2.filter(this.children, clazz::isInstance), clazz::cast);
    }

    public Collection<ExtensionFactory.Id> getExtensionIds() {
        return Collections2.transform(
                this.children, c -> new ExtensionFactory.Id(c.getName(), c.getNamespace()));
    }

    public String findChildContent(String name) {
        Element element = findChild(name);
        return element == null ? null : element.getContent();
    }

    public Element findChild(String name, String xmlns) {
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
                return child;
            }
        }
        return null;
    }

    public String findChildContent(String name, String xmlns) {
        Element element = findChild(name, xmlns);
        return element == null ? null : element.getContent();
    }

    public boolean hasChild(final String name) {
        return findChild(name) != null;
    }

    public boolean hasChild(final String name, final String xmlns) {
        return findChild(name, xmlns) != null;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public Element setChildren(List<Element> children) {
        this.children = children;
        return this;
    }

    public final String getContent() {
        return content;
    }

    public Element setAttribute(final String name, final String value) {
        Preconditions.checkArgument(name != null, "The attribute must have a name");
        if (value == null) {
            this.attributes.remove(name);
        } else {
            this.attributes.put(name, value);
        }
        return this;
    }

    public Element setAttribute(final String name, final Jid value) {
        return this.setAttribute(name, value == null ? null : value.toString());
    }

    public void setAttribute(final String name, final boolean value) {
        this.setAttribute(name, value ? "1" : "0");
    }

    public void removeAttribute(final String name) {
        this.attributes.remove(name);
    }

    public Element setAttributes(Hashtable<String, String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public String getAttribute(String name) {
        if (this.attributes.containsKey(name)) {
            return this.attributes.get(name);
        } else {
            return null;
        }
    }

    public long getLongAttribute(final String name) {
        final var value = Longs.tryParse(Strings.nullToEmpty(this.attributes.get(name)));
        return value == null ? 0 : value;
    }

    public Optional<Integer> getOptionalIntAttribute(final String name) {
        final String value = getAttribute(name);
        if (value == null) {
            return Optional.absent();
        }
        return Optional.fromNullable(Ints.tryParse(value));
    }

    public Jid getAttributeAsJid(final String name) {
        final String jid = this.getAttribute(name);
        if (Strings.isNullOrEmpty(jid)) {
            return null;
        }
        return JidCreate.fromOrThrowUnchecked(jid);
    }

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }

    @NonNull
    public String toString() {
        final StringBuilder elementOutput = new StringBuilder();
        if ((content == null) && (children.size() == 0)) {
            final Tag emptyTag = Tag.empty(name);
            emptyTag.setAttributes(this.attributes);
            elementOutput.append(emptyTag);
        } else {
            final Tag startTag = Tag.start(name);
            startTag.setAttributes(this.attributes);
            elementOutput.append(startTag);
            if (content != null) {
                elementOutput.append(Entities.encode(content));
            } else {
                for (final Element child : children) {
                    elementOutput.append(child.toString());
                }
            }
            final Tag endTag = Tag.end(name);
            elementOutput.append(endTag);
        }
        return elementOutput.toString();
    }

    // TODO should ultimately be removed once everything is an extension
    public final String getName() {
        return name;
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void setAttribute(String name, long value) {
        this.setAttribute(name, Long.toString(value));
    }

    public void setAttribute(String name, int value) {
        this.setAttribute(name, Integer.toString(value));
    }

    public boolean getAttributeAsBoolean(String name) {
        String attr = getAttribute(name);
        return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
    }

    public String getNamespace() {
        return getAttribute("xmlns");
    }
}
