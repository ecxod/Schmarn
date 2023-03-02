package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import im.conversations.android.database.entity.CertificateTrustEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.tls.ScopeFingerprint;

@Dao
public abstract class CertificateTrustDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(final CertificateTrustEntity certificateTrustEntity);

    @Query(
            "SELECT EXISTS (SELECT id FROM certificate_trust WHERE accountId=:account AND"
                    + " scope=:scope AND fingerprint=:fingerprint)")
    protected abstract boolean isTrusted(
            final long account, final String scope, final byte[] fingerprint);

    public boolean isTrusted(final Account account, final ScopeFingerprint scopeFingerprint) {
        return isTrusted(account.id, scopeFingerprint.scope, scopeFingerprint.fingerprint.array());
    }
}
