package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "group")
public class GroupEntity {

    @PrimaryKey @NonNull public Long id;

    @NonNull public String name;

    public static GroupEntity of(final String name) {
        final var entity = new GroupEntity();
        entity.name = name;
        return entity;
    }
}
