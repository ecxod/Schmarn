package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.model.Connection;
import im.conversations.android.database.model.Proxy;

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

    @NonNull public Jid address;
    public String resource;
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
