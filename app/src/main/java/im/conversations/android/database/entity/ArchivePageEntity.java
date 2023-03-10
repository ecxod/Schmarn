package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.jxmpp.jid.Jid;

@Entity(
        tableName = "archive_page",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "archive", "type"},
                    unique = true)
        })
public class ArchivePageEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Jid archive;

    @NonNull public Type type;

    @NonNull public String start;

    @NonNull public String end;

    public boolean reachedMaxPages;

    public static ArchivePageEntity of(
            final Account account,
            final Jid archive,
            final Type type,
            final String start,
            final String end,
            final boolean reachedMaxPages) {
        final var entity = new ArchivePageEntity();
        entity.accountId = account.id;
        entity.archive = archive;
        entity.type = type;
        entity.start = start;
        entity.end = end;
        entity.reachedMaxPages = reachedMaxPages;
        return entity;
    }

    public enum Type {
        START,
        MIDDLE,
        LIVE
    }
}
