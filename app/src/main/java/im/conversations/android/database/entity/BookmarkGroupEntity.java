package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "bookmark_group",
        primaryKeys = {"bookmarkId", "groupId"},
        foreignKeys = {
            @ForeignKey(
                    entity = BookmarkEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"bookmarkId"},
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(
                    entity = GroupEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"groupId"},
                    onDelete = ForeignKey.RESTRICT),
        },
        indices = {@Index(value = "groupId")})
public class BookmarkGroupEntity {

    @NonNull public Long bookmarkId;

    @NonNull public Long groupId;

    public static BookmarkGroupEntity of(long bookmarkId, final long groupId) {
        final var entity = new BookmarkGroupEntity();
        entity.bookmarkId = bookmarkId;
        entity.groupId = groupId;
        return entity;
    }
}
