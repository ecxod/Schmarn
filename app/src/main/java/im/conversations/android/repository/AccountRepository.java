package im.conversations.android.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.IDs;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.entity.CertificateTrustEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.AccountIdentifier;
import im.conversations.android.database.model.Connection;
import im.conversations.android.tls.ScopeFingerprint;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.RegistrationManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
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
        return account;
    }

    public ListenableFuture<Account> createAccountAsync(
            final @NonNull BareJid address, final String password, final boolean loginAndBind) {
        final var creationFuture =
                Futures.submit(() -> createAccount(address, password, loginAndBind), IO_EXECUTOR);
        return Futures.transformAsync(
                creationFuture,
                account ->
                        Futures.transform(
                                ConnectionPool.getInstance(context).reconfigure(),
                                v -> account,
                                MoreExecutors.directExecutor()),
                MoreExecutors.directExecutor());
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

    public ListenableFuture<Void> deleteAccountAsync(@NonNull Account account) {
        return Futures.submit(() -> deleteAccount(account), database.getQueryExecutor());
    }

    private Void deleteAccount(@NonNull Account account) {
        database.accountDao().delete(account.id);
        ConnectionPool.getInstance(context).reconfigure();
        return null;
    }

    public ListenableFuture<XmppConnection> getConnectedFuture(@NonNull final Account account) {
        final var optional = ConnectionPool.getInstance(context).get(account);
        if (optional.isPresent()) {
            return optional.get().asConnectedFuture(false);
        } else {
            return Futures.immediateFailedFuture(
                    new IllegalStateException(
                            String.format("Account %s is not configured", account.address)));
        }
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

    public ListenableFuture<Account> setConnectionAsync(
            final Account account, final Connection connection) {
        return Futures.submit(
                () -> setConnection(account, connection), database.getQueryExecutor());
    }

    public Account setConnection(final Account account, final Connection connection) {
        database.accountDao().setConnection(account, connection);
        ConnectionPool.getInstance(context).reconnect(account);
        return account;
    }

    public void reconnect(final Account account) {
        ConnectionPool.getInstance(context).reconnect(account);
    }

    public LiveData<List<AccountIdentifier>> getAccounts() {
        return database.accountDao().getAccounts();
    }

    public LiveData<Boolean> hasNoAccounts() {
        return database.accountDao().hasNoAccounts();
    }

    public ListenableFuture<Void> setCertificateTrustedAsync(
            final Account account, final ScopeFingerprint scopeFingerprint) {
        return Futures.submit(
                () -> setCertificateTrusted(account, scopeFingerprint),
                database.getQueryExecutor());
    }

    private void setCertificateTrusted(
            final Account account, final ScopeFingerprint scopeFingerprint) {
        this.database
                .certificateTrustDao()
                .insert(CertificateTrustEntity.of(account.id, scopeFingerprint));
    }

    public static class AccountAlreadyExistsException extends IllegalStateException {
        public AccountAlreadyExistsException(BareJid address) {
            super(String.format("The account %s has already been setup", address));
        }
    }
}
