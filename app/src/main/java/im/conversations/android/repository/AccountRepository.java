package im.conversations.android.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.IDs;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.RegistrationManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.jxmpp.jid.BareJid;

public class AccountRepository extends AbstractRepository {

    public AccountRepository(final Context context) {
        super(context);
    }

    private Account createAccount(
            @NonNull final BareJid address, final String password, final boolean loginAndBind)
            throws GeneralSecurityException, IOException {
        if (database.accountDao().hasAccount(address)) {
            throw new AccountAlreadyExistsException(address);
        }
        final byte[] randomSeed = IDs.seed();
        final var entity = new AccountEntity();
        entity.address = address;
        entity.enabled = true;
        entity.loginAndBind = loginAndBind;
        entity.randomSeed = randomSeed;
        final long id = database.accountDao().insert(entity);
        final var account = new Account(id, address, entity.randomSeed);
        if (password != null) {
            CredentialStore.getInstance(context).setPassword(account, password);
        }
        ConnectionPool.getInstance(context).reconfigure();
        return account;
    }

    public ListenableFuture<Account> createAccountAsync(
            final @NonNull BareJid address, final String password, final boolean loginAndBind) {
        return Futures.submit(() -> createAccount(address, password, loginAndBind), IO_EXECUTOR);
    }

    public ListenableFuture<Account> createAccountAsync(
            final @NonNull BareJid address, final String password) {
        return createAccountAsync(address, password, true);
    }

    public ListenableFuture<RegistrationManager.Registration> getRegistration(
            @NonNull final Account account) {
        final ListenableFuture<XmppConnection> connectedFuture = getConnectedFuture(account);
        return Futures.transformAsync(
                connectedFuture,
                xc -> xc.getManager(RegistrationManager.class).getRegistration(),
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Boolean> deleteAccountAsync(@NonNull Account account) {
        return Futures.submit(() -> deleteAccount(account), IO_EXECUTOR);
    }

    private Boolean deleteAccount(@NonNull Account account) {
        return database.accountDao().delete(account.id) > 0;
    }

    public ListenableFuture<XmppConnection> getConnectedFuture(@NonNull final Account account) {
        return ConnectionPool.getInstance(context).get(account).asConnectedFuture();
    }

    public ListenableFuture<Account> setPasswordAsync(
            @NonNull Account account, @NonNull String password) {
        return Futures.submit(() -> setPassword(account, password), IO_EXECUTOR);
    }

    private Account setPassword(@NonNull Account account, @NonNull String password)
            throws GeneralSecurityException, IOException {
        CredentialStore.getInstance(context).setPassword(account, password);
        ConnectionPool.getInstance(context).reconnect(account);
        return account;
    }

    public static class AccountAlreadyExistsException extends IllegalStateException {
        public AccountAlreadyExistsException(BareJid address) {
            super(String.format("The account %s has already been setup", address));
        }
    }
}
