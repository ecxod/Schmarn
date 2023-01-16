package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(value = {"accountId", "capsHash"}),
            @Index(
                    value = {"accountId", "caps2HashSha256"},
                    unique = true)
        })
public class DiscoEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    public byte[] capsHash;
    public byte[] caps2HashSha256;

    public static DiscoEntity of(
            final long accountId, final byte[] capsHash, final byte[] caps2HashSha256) {
        final var entity = new DiscoEntity();
        entity.accountId = accountId;
        entity.capsHash = capsHash;
        entity.caps2HashSha256 = caps2HashSha256;
        return entity;
    }
}
