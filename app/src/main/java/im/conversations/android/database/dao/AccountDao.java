package im.conversations.android.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.Connection;
import java.util.List;
import org.jxmpp.jid.BareJid;

@Dao
public interface AccountDao {

    @Query("SELECT EXISTS (SELECT id FROM account WHERE address=:address)")
    boolean hasAccount(BareJid address);

    @Insert
    long insert(final AccountEntity account);

    @Query("SELECT id,address,randomSeed FROM account WHERE enabled = 1")
    ListenableFuture<List<Account>> getEnabledAccounts();

    @Query("SELECT id,address,randomSeed FROM account WHERE address=:address AND enabled=1")
    ListenableFuture<Account> getEnabledAccount(BareJid address);

    @Query("SELECT id,address,randomSeed FROM account WHERE id=:id AND enabled=1")
    ListenableFuture<Account> getEnabledAccount(long id);

    @Query("SELECT id,address FROM account")
    LiveData<List<AccountIdentifier>> getAccounts();

    @Query("SELECT hostname,port,directTls FROM account WHERE id=:id AND hostname != null")
    Connection getConnectionSettings(long id);

    @Query("SELECT resource FROM account WHERE id=:id")
    String getResource(long id);

    @Query("SELECT rosterVersion FROM account WHERE id=:id")
    String getRosterVersion(long id);

    @Query("SELECT quickStartAvailable FROM account where id=:id")
    boolean quickStartAvailable(long id);

    @Query("SELECT loginAndBind FROM account where id=:id")
    boolean loginAndBind(long id);

    @Query(
            "UPDATE account set quickStartAvailable=:available WHERE id=:id AND"
                    + " quickStartAvailable != :available")
    void setQuickStartAvailable(long id, boolean available);

    @Query(
            "UPDATE account set loginAndBind=:loginAndBind WHERE id=:id AND"
                    + " loginAndBind != :loginAndBind")
    void setLoginAndBind(long id, boolean loginAndBind);

    @Query(
            "UPDATE account set showErrorNotification=:showErrorNotification WHERE id=:id AND"
                    + " showErrorNotification != :showErrorNotification")
    int setShowErrorNotification(long id, boolean showErrorNotification);

    @Query("UPDATE account set resource=:resource WHERE id=:id")
    void setResource(long id, String resource);

    @Query("DELETE FROM account WHERE id=:id")
    int delete(long id);

    // TODO on disable set resource to null
}
