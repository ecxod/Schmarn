package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Connection;
import im.conversations.android.database.model.Proxy;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.parts.Resourcepart;

@Entity(
        tableName = "account",
        indices = {
            @Index(
                    value = {"address"},
                    unique = true)
        })
public class AccountEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public BareJid address;
    public Resourcepart resource;
    public byte[] randomSeed;

    public boolean enabled;

    public boolean quickStartAvailable = false;
    public boolean loginAndBind = true;

    public boolean showErrorNotification = true;

    public String rosterVersion;

    @Embedded public Connection connection;

    @Embedded(prefix = "proxy")
    public Proxy proxy;
}
