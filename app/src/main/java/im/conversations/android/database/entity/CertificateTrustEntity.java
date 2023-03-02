package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.tls.ScopeFingerprint;

@Entity(
        tableName = "certificate_trust",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "scope"},
                    unique = true)
        })
public class CertificateTrustEntity {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public String scope;

    @NonNull public byte[] fingerprint;

    public static CertificateTrustEntity of(
            final long accountId, final ScopeFingerprint scopeFingerprint) {
        final var entity = new CertificateTrustEntity();
        entity.accountId = accountId;
        entity.scope = scopeFingerprint.scope;
        entity.fingerprint = scopeFingerprint.fingerprint.array();
        return entity;
    }
}
