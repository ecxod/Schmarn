package im.conversations.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.util.concurrent.ListenableFuture;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.Connection;
import java.util.List;
import org.jxmpp.jid.BareJid;

@Dao
public abstract class AccountDao {

    @Query("SELECT EXISTS (SELECT id FROM account WHERE address=:address)")
    public abstract boolean hasAccount(BareJid address);

    @Query("SELECT NOT EXISTS (SELECT id FROM account)")
    public abstract LiveData<Boolean> hasNoAccounts();

    @Insert
    public abstract long insert(final AccountEntity account);

    @Query("SELECT id,address,randomSeed FROM account WHERE enabled = 1")
    public abstract ListenableFuture<List<Account>> getEnabledAccounts();

    @Query("SELECT id,address,randomSeed FROM account WHERE address=:address AND enabled=1")
    public abstract ListenableFuture<Account> getEnabledAccount(BareJid address);

    @Query("SELECT id,address,randomSeed FROM account WHERE id=:id AND enabled=1")
    public abstract ListenableFuture<Account> getEnabledAccount(long id);

    @Query("SELECT id,address FROM account")
    public abstract LiveData<List<AccountIdentifier>> getAccounts();

    @Query("SELECT hostname,port,directTls FROM account WHERE id=:id AND hostname IS NOT null")
    public abstract Connection getConnectionSettings(long id);

    @Query("SELECT resource FROM account WHERE id=:id")
    public abstract String getResource(long id);

    @Query("SELECT rosterVersion FROM account WHERE id=:id")
    public abstract String getRosterVersion(long id);

    @Query("SELECT quickStartAvailable FROM account where id=:id")
    public abstract boolean quickStartAvailable(long id);

    @Query("SELECT loginAndBind FROM account where id=:id")
    public abstract boolean loginAndBind(long id);

    @Query(
            "UPDATE account set quickStartAvailable=:available WHERE id=:id AND"
                    + " quickStartAvailable != :available")
    public abstract void setQuickStartAvailable(long id, boolean available);

    @Query(
            "UPDATE account set loginAndBind=:loginAndBind WHERE id=:id AND"
                    + " loginAndBind != :loginAndBind")
    public abstract void setLoginAndBind(long id, boolean loginAndBind);

    @Query(
            "UPDATE account set showErrorNotification=:showErrorNotification WHERE id=:id AND"
                    + " showErrorNotification != :showErrorNotification")
    public abstract int setShowErrorNotification(long id, boolean showErrorNotification);

    @Query("UPDATE account set resource=:resource WHERE id=:id")
    public abstract void setResource(long id, String resource);

    @Query("DELETE FROM account WHERE id=:id")
    public abstract int delete(long id);

    @Query(
            "UPDATE account SET hostname=:hostname, port=:port, directTls=:directTls WHERE"
                    + " id=:account")
    protected abstract int setConnection(
            long account, String hostname, int port, boolean directTls);

    @Transaction
    public void setConnection(final Account account, final Connection connection) {
        final var count =
                setConnection(
                        account.id, connection.hostname, connection.port, connection.directTls);
        if (count != 1) {
            throw new IllegalStateException("Could not update account");
        }
    }

    // TODO on disable set resource to null
}
