package im.conversations.android.xmpp.model.data;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

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
}
