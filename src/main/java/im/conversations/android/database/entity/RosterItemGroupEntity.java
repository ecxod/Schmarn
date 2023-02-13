package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "roster_group",
        foreignKeys =
                @ForeignKey(
                        entity = RosterItemEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"rosterItemId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "rosterItemId")})
public class RosterItemGroupEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long rosterItemId;

    public String name;

    public static RosterItemGroupEntity of(long rosterItemId, final String name) {
        final var entity = new RosterItemGroupEntity();
        entity.rosterItemId = rosterItemId;
        entity.name = name;
        return entity;
    }
}
