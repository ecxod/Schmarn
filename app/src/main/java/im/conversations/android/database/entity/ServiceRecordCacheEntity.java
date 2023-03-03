package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import im.conversations.android.dns.ServiceRecord;

@Entity(
        tableName = "service_record_cache",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "domain"},
                    unique = true)
        })
public class ServiceRecordCacheEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public String domain;

    @Embedded @NonNull public ServiceRecord serviceRecord;

    public static ServiceRecordCacheEntity of(
            final Account account, final String domain, final ServiceRecord serviceRecord) {
        final var entity = new ServiceRecordCacheEntity();
        entity.accountId = account.id;
        entity.domain = domain;
        entity.serviceRecord = serviceRecord;
        return entity;
    }
}
