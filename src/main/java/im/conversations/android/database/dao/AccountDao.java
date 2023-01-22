package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Connection;
import java.util.List;

@Dao
public interface AccountDao {

    @Insert
    long insert(final AccountEntity account);

    @Query("SELECT id,address,randomSeed FROM account WHERE enabled = 1")
    ListenableFuture<List<Account>> getEnabledAccounts();

    @Query("SELECT id,address,randomSeed FROM account WHERE address=:address AND enabled=1")
    ListenableFuture<Account> getEnabledAccount(Jid address);

    @Query("SELECT id,address,randomSeed FROM account WHERE id=:id AND enabled=1")
    ListenableFuture<Account> getEnabledAccount(long id);

    @Query("SELECT hostname,port,directTls FROM account WHERE id=:id AND hostname != null")
    Connection getConnectionSettings(long id);

    @Query("SELECT resource FROM account WHERE id=:id")
    String getResource(long id);

    @Query("SELECT rosterVersion FROM account WHERE id=:id")
    String getRosterVersion(long id);

    @Query("SELECT quickStartAvailable FROM account where id=:id")
    boolean quickStartAvailable(long id);

    @Query("SELECT pendingRegistration FROM account where id=:id")
    boolean pendingRegistration(long id);

    @Query("SELECT loggedInSuccessfully == 0 FROM account where id=:id")
    boolean isInitialLogin(long id);

    @Query(
            "UPDATE account set quickStartAvailable=:available WHERE id=:id AND"
                    + " quickStartAvailable != :available")
    void setQuickStartAvailable(long id, boolean available);

    @Query(
            "UPDATE account set pendingRegistration=:pendingRegistration WHERE id=:id AND"
                    + " pendingRegistration != :pendingRegistration")
    void setPendingRegistration(long id, boolean pendingRegistration);

    @Query(
            "UPDATE account set loggedInSuccessfully=:loggedInSuccessfully WHERE id=:id AND"
                    + " loggedInSuccessfully != :loggedInSuccessfully")
    int setLoggedInSuccessfully(long id, boolean loggedInSuccessfully);

    @Query(
            "UPDATE account set showErrorNotification=:showErrorNotification WHERE id=:id AND"
                    + " showErrorNotification != :showErrorNotification")
    int setShowErrorNotification(long id, boolean showErrorNotification);

    @Query("UPDATE account set resource=:resource WHERE id=:id")
    void setResource(long id, String resource);

    // TODO on disable set resource to null
}
