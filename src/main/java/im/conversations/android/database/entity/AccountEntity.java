package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
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

    @NonNull public String address;
    public String resource;
    public byte[] randomSeed;

    public boolean enabled;

    public boolean quickStartAvailable = false;
    public boolean pendingRegistration = false;

    // TODO this is only used during setup; depending on how the setup procedure will look in the
    // future we might get rid of this property
    public boolean loggedInSuccessfully = false;

    public boolean showErrorNotification = true;

    public String rosterVersion;

    @Embedded public Connection connection;

    @Embedded(prefix = "proxy")
    public Proxy proxy;
}
