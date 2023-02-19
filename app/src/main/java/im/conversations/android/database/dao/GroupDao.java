package im.conversations.android.database.dao;

import androidx.room.Insert;
import androidx.room.Query;
import im.conversations.android.database.entity.GroupEntity;

public abstract class GroupDao {

    public long getOrCreateId(final String name) {
        final Long existing = getGroupId(name);
        if (existing != null) {
            return existing;
        }
        return insert(GroupEntity.of(name));
    }

    @Query("SELECT id FROM `group` WHERE name=:name")
    abstract Long getGroupId(final String name);

    @Insert
    abstract Long insert(GroupEntity groupEntity);

    @Query(
            "DELETE from `group` WHERE id NOT IN(SELECT groupId FROM roster_group) AND id NOT"
                    + " IN(SELECT groupId FROM bookmark_group)")
    abstract void deleteEmptyGroups();
}
