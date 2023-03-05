package im.conversations.android.database.model;

import androidx.room.Relation;
import com.google.common.collect.Iterables;
import im.conversations.android.database.entity.DiscoExtensionFieldEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldValueEntity;
import java.util.List;

public class DiscoExtension {

    public long id;
    public String type;

    @Relation(
            entity = DiscoExtensionFieldEntity.class,
            parentColumn = "id",
            entityColumn = "extensionId")
    public List<Field> fields;

    public Field getField(final String name) {
        return Iterables.find(fields, f -> name.equals(f.field), null);
    }

    public static class Field {

        public long id;
        public String field;

        @Relation(
                entity = DiscoExtensionFieldValueEntity.class,
                parentColumn = "id",
                entityColumn = "fieldId",
                projection = {"value"})
        public List<String> values;
    }
}
