package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import im.conversations.android.database.entity.ServiceRecordCacheEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.dns.ServiceRecord;

@Dao
public abstract class ServiceRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insert(ServiceRecordCacheEntity entity);

    public void insert(final Account account, final ServiceRecord serviceRecord) {
        insert(
                ServiceRecordCacheEntity.of(
                        account, account.address.getDomain().toString(), serviceRecord));
    }

    @Query(
            "SELECT ip,hostname,port,directTls,priority,authenticated FROM service_record_cache"
                    + " WHERE accountId=:account AND domain=:domain LIMIT 1")
    protected abstract ServiceRecord getCachedServiceRecord(
            final long account, final String domain);

    public ServiceRecord getCachedServiceRecord(final Account account) {
        return getCachedServiceRecord(account.id, account.address.getDomain().toString());
    }
}
