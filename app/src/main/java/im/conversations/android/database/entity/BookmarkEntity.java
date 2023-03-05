package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.common.base.Strings;
import im.conversations.android.xmpp.model.bookmark.Conference;
import im.conversations.android.xmpp.model.bookmark.Nick;
import java.util.Map;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

@Entity(
        tableName = "bookmark",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "address"},
                    unique = true)
        })
public class BookmarkEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Jid address;

    public String name;

    public String nick;

    public boolean autoJoin;

    public String password;

    public static BookmarkEntity of(
            final long accountId, final Map.Entry<String, Conference> entry) {
        final var address = JidCreate.fromOrNull(entry.getKey());
        final var conference = entry.getValue();
        if (address == null) {
            return null;
        }
        final Nick nick = conference.getNick();
        final var entity = new BookmarkEntity();
        entity.accountId = accountId;
        entity.address = address;
        entity.autoJoin = conference.isAutoJoin();
        entity.name = conference.getConferenceName();
        entity.nick = Strings.emptyToNull(nick == null ? null : nick.getContent());
        return entity;
    }
}
