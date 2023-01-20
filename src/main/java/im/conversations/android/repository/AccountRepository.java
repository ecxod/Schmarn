package im.conversations.android.repository;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.IDs;
import im.conversations.android.database.CredentialStore;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.model.Account;

public class AccountRepository extends AbstractRepository {

    public AccountRepository(final Context context) {
        super(context);
    }

    public Account createAccount(@NonNull final Jid address, final String password) {
        Preconditions.checkArgument(
                address.isBareJid(), "Account should be specified without resource");
        Preconditions.checkArgument(password != null, "Missing password");
        final byte[] randomSeed = IDs.seed();
        final var entity = new AccountEntity();
        entity.address = address;
        entity.enabled = true;
        entity.randomSeed = randomSeed;
        final long id = database.accountDao().insert(entity);
        final var account = new Account(id, address, entity.randomSeed);
        try {
            CredentialStore.getInstance(context).setPassword(account, password);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not store password", e);
        }
        return account;
    }
}
