package im.conversations.android.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.IDs;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.ConnectionPool;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.RegistrationManager;

public class AccountRepository extends AbstractRepository {

    public AccountRepository(final Context context) {
        super(context);
    }

    private Account createAccount(
            @NonNull final Jid address, final String password, final boolean loginAndBind) {
        Preconditions.checkArgument(
                address.isBareJid(), "Account should be specified without resource");
        Preconditions.checkArgument(password != null, "Missing password");
        final byte[] randomSeed = IDs.seed();
        final var entity = new AccountEntity();
        entity.address = address;
        entity.enabled = true;
        entity.loginAndBind = loginAndBind;
        entity.randomSeed = randomSeed;
        final long id = database.accountDao().insert(entity);
        final var account = new Account(id, address, entity.randomSeed);
        try {
            CredentialStore.getInstance(context).setPassword(account, password);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not store password", e);
        }
        ConnectionPool.getInstance(context).reconfigure(account);
        return account;
    }

    public ListenableFuture<Account> createAccountAsync(
            final @NonNull Jid address, final String password, final boolean loginAndBind) {
        return Futures.submit(() -> createAccount(address, password, loginAndBind), IO_EXECUTOR);
    }

    public ListenableFuture<Account> createAccountAsync(
            final @NonNull Jid address, final String password) {
        return createAccountAsync(address, password, true);
    }

    public ListenableFuture<RegistrationManager.Registration> getRegistration(
            final Account account) {
        final ListenableFuture<XmppConnection> connectedFuture =
                ConnectionPool.getInstance(context).reconfigure(account).asConnectedFuture();
        return Futures.transformAsync(
                connectedFuture,
                xc -> xc.getManager(RegistrationManager.class).getRegistration(),
                MoreExecutors.directExecutor());
    }
}
