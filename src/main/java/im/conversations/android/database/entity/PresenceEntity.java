package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.model.PresenceShow;
import im.conversations.android.database.model.PresenceType;

@Entity(
        tableName = "presence",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "address", "resource"},
                    unique = true)
        })
public class PresenceEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public String address;

    @Nullable public String resource;

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
}
