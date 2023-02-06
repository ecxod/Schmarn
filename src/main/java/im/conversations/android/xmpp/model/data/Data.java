package im.conversations.android.xmpp.model.data;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Map;

@XmlElement(name = "x")
public class Data extends Extension {

    private static final String FORM_TYPE = "FORM_TYPE";

    public Data() {
        super(Data.class);
    }

    public String getFormType() {
        final var fields = this.getExtensions(Field.class);
        final var formTypeField = Iterables.find(fields, f -> FORM_TYPE.equals(f.getFieldName()));
        return Iterables.getFirst(formTypeField.getValues(), null);
    }

    public Collection<Field> getFields() {
        return Collections2.filter(
                this.getExtensions(Field.class), f -> !FORM_TYPE.equals(f.getFieldName()));
    }

    private void addField(final String name, final Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Null values are not supported on data fields");
        }
        final var field = this.addExtension(new Field());
        field.setFieldName(name);
        final var valueExtension = field.addExtension(new Value());
        if (value instanceof String) {
            valueExtension.setContent((String) value);
        } else if (value instanceof Integer) {
            valueExtension.setContent(String.valueOf(value));
        } else if (value instanceof Boolean) {
            valueExtension.setContent(Boolean.TRUE.equals(value) ? "true" : "false");
        } else {
            throw new IllegalArgumentException(
                    String.format(
                            "%s is not a supported field value", value.getClass().getSimpleName()));
        }
    }

    private void setFormType(final String formType) {
        this.addField(FORM_TYPE, formType);
    }

    public static Data of(final String formType, final Map<String, Object> values) {
        final var data = new Data();
        data.setFormType(formType);
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            data.addField(entry.getKey(), entry.getValue());
        }
        return data;
    }
}
