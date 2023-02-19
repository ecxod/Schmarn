package im.conversations.android.database.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.util.StringJoiner;

public class GroupIdentifier implements ChatFilter {

    public final long id;
    public final String name;


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .toString();
    }

    public GroupIdentifier(long id, String name) {
        this.id = id;
        this.name = name;
    }
}
