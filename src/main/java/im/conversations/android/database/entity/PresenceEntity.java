package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.entities.MucOptions;
import im.conversations.android.database.model.PresenceShow;
import im.conversations.android.database.model.PresenceType;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;

@Entity(
        tableName = "presence",
        foreignKeys = {
            @ForeignKey(
                    entity = AccountEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"accountId"},
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(
                    entity = DiscoEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"discoId"},
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {
            @Index(
                    value = {"accountId", "address", "resource"},
                    unique = true),
            @Index(value = {"discoId"})
        })
public class PresenceEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public BareJid address;

    @NonNull public Resourcepart resource;

    @Nullable public PresenceType type;

    @Nullable public PresenceShow show;

    @Nullable public String status;

    @Nullable public String vCardPhoto;

    @Nullable public String occupantId;

    @Nullable public MucOptions.Affiliation mucUserAffiliation;

    @Nullable public MucOptions.Role mucUserRole;

    @Nullable public Jid mucUserJid;

    // set to true if presence has status code 110 (this means we are online)
    public boolean mucUserSelf;

    public Long discoId;

    public static PresenceEntity of(
            long account,
            @NonNull BareJid address,
            @NonNull Resourcepart resource,
            PresenceType type,
            PresenceShow show,
            String status) {
        final var entity = new PresenceEntity();
        entity.accountId = account;
        entity.address = address;
        entity.resource = resource;
        entity.type = type;
        entity.show = show;
        entity.status = status;
        return entity;
    }
}
