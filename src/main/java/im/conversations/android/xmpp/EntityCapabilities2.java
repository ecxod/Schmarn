package im.conversations.android.xmpp;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Ordering;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Bytes;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.data.Field;
import im.conversations.android.xmpp.model.data.Value;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class EntityCapabilities2 {

    private static final char UNIT_SEPARATOR = 0x1f;
    private static final char RECORD_SEPARATOR = 0x1e;

    private static final char GROUP_SEPARATOR = 0x1d;

    private static final char FILE_SEPARATOR = 0x1c;

    public static byte[] hash(final InfoQuery info) {
        final String algo = algorithm(info);
        return Hashing.sha256().hashString(algo, StandardCharsets.UTF_8).asBytes();
    }

    private static String asHex(final String message) {
        return Joiner.on(' ')
                .join(
                        Collections2.transform(
                                Bytes.asList(message.getBytes(StandardCharsets.UTF_8)),
                                b -> String.format("%02x", b)));
    }

    private static String algorithm(final InfoQuery infoQuery) {
        return features(infoQuery.getExtensions(Feature.class))
                + identities(infoQuery.getExtensions(Identity.class))
                + extensions(infoQuery.getExtensions(Data.class));
    }

    private static String identities(final Collection<Identity> identities) {
        return Joiner.on("")
                        .join(
                                Ordering.natural()
                                        .sortedCopy(
                                                Collections2.transform(
                                                        identities, EntityCapabilities2::identity)))
                + FILE_SEPARATOR;
    }

    private static String identity(final Identity identity) {
        return Strings.nullToEmpty(identity.getCategory())
                + UNIT_SEPARATOR
                + Strings.nullToEmpty(identity.getType())
                + UNIT_SEPARATOR
                + Strings.nullToEmpty(identity.getLang())
                + UNIT_SEPARATOR
                + Strings.nullToEmpty(identity.getIdentityName())
                + UNIT_SEPARATOR
                + RECORD_SEPARATOR;
    }

    private static String features(Collection<Feature> features) {
        return Joiner.on("")
                        .join(
                                Ordering.natural()
                                        .sortedCopy(
                                                Collections2.transform(
                                                        features, EntityCapabilities2::feature)))
                + FILE_SEPARATOR;
    }

    private static String feature(final Feature feature) {
        return Strings.nullToEmpty(feature.getVar()) + UNIT_SEPARATOR;
    }

    private static String value(final Value value) {
        return Strings.nullToEmpty(value.getContent()) + UNIT_SEPARATOR;
    }

    private static String values(final Collection<Value> values) {
        return Joiner.on("")
                .join(
                        Ordering.natural()
                                .sortedCopy(
                                        Collections2.transform(
                                                values, EntityCapabilities2::value)));
    }

    private static String field(final Field field) {
        return Strings.nullToEmpty(field.getFieldName())
                + UNIT_SEPARATOR
                + values(field.getExtensions(Value.class))
                + RECORD_SEPARATOR;
    }

    private static String fields(final Collection<Field> fields) {
        return Joiner.on("")
                        .join(
                                Ordering.natural()
                                        .sortedCopy(
                                                Collections2.transform(
                                                        fields, EntityCapabilities2::field)))
                + GROUP_SEPARATOR;
    }

    private static String extension(final Data data) {
        return fields(data.getExtensions(Field.class));
    }

    private static String extensions(final Collection<Data> extensions) {
        return Joiner.on("")
                        .join(
                                Ordering.natural()
                                        .sortedCopy(
                                                Collections2.transform(
                                                        extensions,
                                                        EntityCapabilities2::extension)))
                + FILE_SEPARATOR;
    }
}
