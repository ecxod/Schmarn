package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.AxolotlDeviceListEntity;
import im.conversations.android.database.entity.AxolotlDeviceListItemEntity;
import im.conversations.android.database.entity.AxolotlIdentityEntity;
import im.conversations.android.database.entity.AxolotlIdentityKeyPairEntity;
import im.conversations.android.database.entity.AxolotlPreKeyEntity;
import im.conversations.android.database.entity.AxolotlSessionEntity;
import im.conversations.android.database.entity.AxolotlSignedPreKeyEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.error.Condition;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

@Dao
public abstract class AxolotlDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract long insert(AxolotlDeviceListEntity entity);

    @Insert
    protected abstract void insert(Collection<AxolotlDeviceListItemEntity> entities);

    @Transaction
    public void setDeviceList(Account account, Jid from, Set<Integer> deviceIds) {
        final var listId = insert(AxolotlDeviceListEntity.of(account.id, from));
        insert(
                Collections2.transform(
                        deviceIds, deviceId -> AxolotlDeviceListItemEntity.of(listId, deviceId)));
    }

    @Query(
            "SELECT EXISTS(SELECT deviceId FROM axolotl_device_list JOIN axolotl_device_list_item"
                    + " ON axolotl_device_list.id=axolotl_device_list_item.deviceListId WHERE"
                    + " accountId=:account AND address=:address AND deviceId=:deviceId)")
    public abstract boolean hasDeviceId(final long account, final Jid address, final int deviceId);

    @Transaction
    public void setDeviceListError(final Account account, final Jid address, Condition condition) {
        insert(AxolotlDeviceListEntity.of(account.id, address, condition.getName()));
    }

    @Transaction
    public void setDeviceListParsingError(final Account account, final Jid address) {
        insert(AxolotlDeviceListEntity.ofParsingIssue(account.id, address));
    }

    @Transaction
    public IdentityKeyPair getOrCreateIdentityKeyPair(final Account account) {
        final var existing = getIdentityKeyPair(account.id);
        if (existing != null) {
            return existing;
        }
        final var ecKeyPair = Curve.generateKeyPair();
        final var identityKeyPair =
                new IdentityKeyPair(
                        new IdentityKey(ecKeyPair.getPublicKey()), ecKeyPair.getPrivateKey());
        insert(AxolotlIdentityKeyPairEntity.of(account, identityKeyPair));
        return identityKeyPair;
    }

    @Insert
    protected abstract void insert(AxolotlIdentityKeyPairEntity entity);

    @Query("SELECT identityKeyPair FROM axolotl_identity_key_pair WHERE accountId=:account")
    protected abstract IdentityKeyPair getIdentityKeyPair(long account);

    @Query(
            "SELECT signedPreKeyRecord FROM axolotl_signed_pre_key WHERE accountId=:account AND"
                    + " signedPreKeyId=:signedPreKeyId")
    public abstract SignedPreKeyRecord getSignedPreKey(long account, int signedPreKeyId);

    @Query(
            "SELECT NOT EXISTS(SELECT signedPreKeyRecord FROM axolotl_signed_pre_key WHERE"
                    + " accountId=:account AND signedPreKeyId=:signedPreKeyId)")
    public abstract boolean hasNotSignedPreKey(long account, int signedPreKeyId);

    @Query(
            "SELECT signedPreKeyRecord FROM axolotl_signed_pre_key WHERE accountId=:account ORDER"
                    + " BY signedPreKeyId DESC LIMIT 1")
    public abstract SignedPreKeyRecord getLatestSignedPreKey(long account);

    @Transaction
    public boolean setIdentity(
            Account account, Jid address, int deviceId, IdentityKey identityKey) {
        final var existing = getIdentityKey(account.id, address, deviceId);
        if (existing == null || !existing.equals(identityKey)) {
            insert(AxolotlIdentityEntity.of(account, address, deviceId, identityKey));
            return true;
        } else {
            return false;
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insert(AxolotlIdentityEntity axolotlIdentityEntity);

    @Query(
            "SELECT identityKey FROM AXOLOTL_IDENTITY WHERE accountId=:account AND"
                    + " address=:address AND deviceId=:deviceId")
    protected abstract IdentityKey getIdentityKey(long account, Jid address, int deviceId);

    @Query(
            "SELECT preKeyRecord FROM axolotl_pre_key WHERE accountId=:account AND"
                    + " preKeyid=:preKeyId")
    public abstract PreKeyRecord getPreKey(long account, int preKeyId);

    @Query("SELECT MAX(preKeyId) FROM axolotl_pre_key WHERE accountId=:account")
    public abstract Integer getMaxPreKeyId(final long account);

    @Query("SELECT COUNT(id) FROM axolotl_pre_key WHERE accountId=:account AND removed=0")
    public abstract int getExistingPreKeyCount(final long account);

    public void setPreKey(final Account account, int preKeyId, PreKeyRecord preKeyRecord) {
        insert(AxolotlPreKeyEntity.of(account, preKeyId, preKeyRecord));
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insert(AxolotlPreKeyEntity axolotlPreKeyEntity);

    public void setPreKeys(final Account account, final Collection<PreKeyRecord> preKeyRecords) {
        insertPreKeys(
                Collections2.transform(
                        preKeyRecords, r -> AxolotlPreKeyEntity.of(account, r.getId(), r)));
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insertPreKeys(Collection<AxolotlPreKeyEntity> entities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insert(AxolotlSessionEntity axolotlSessionEntity);

    @Query(
            "SELECT EXISTS(SELECT id FROM axolotl_pre_key WHERE accountId=:account AND"
                    + " preKeyId=:preKeyId)")
    public abstract boolean hasPreKey(long account, int preKeyId);

    @Query(
            "SELECT EXISTS(SELECT id FROM axolotl_signed_pre_key WHERE accountId=:account AND"
                    + " signedPreKeyId=:signedPreKeyId)")
    public abstract boolean hasSignedPreKey(long account, int signedPreKeyId);

    @Query("UPDATE axolotl_pre_key SET removed=1 WHERE accountId=:account AND preKeyId=:preKeyId")
    public abstract void markPreKeyAsRemoved(long account, int preKeyId);

    @Query(
            "UPDATE axolotl_signed_pre_key SET removed=1 WHERE accountId=:account AND"
                    + " signedPreKeyId=:signedPreKeyId")
    public abstract void markSignedPreKeyAsRemoved(long account, int signedPreKeyId);

    @Query(
            "SELECT sessionRecord FROM axolotl_session WHERE accountId=:account AND"
                    + " address=:address AND deviceId=:deviceId")
    public abstract SessionRecord getSessionRecord(long account, Jid address, int deviceId);

    @Query("SELECT deviceId FROM axolotl_session WHERE accountId=:account AND address=:address")
    public abstract List<Integer> getSessionDeviceIds(long account, String address);

    public void setSessionRecord(Account account, Jid address, int deviceId, SessionRecord record) {
        insert(AxolotlSessionEntity.of(account, address, deviceId, record));
    }

    @Query(
            "SELECT EXISTS(SELECT id FROM axolotl_session WHERE accountId=:account AND"
                    + " address=:address AND deviceId=:deviceId)")
    public abstract boolean hasSession(long account, Jid address, int deviceId);

    @Query(
            "DELETE FROM axolotl_session WHERE accountId=:account AND address=:address AND"
                    + " deviceId=:deviceId")
    public abstract void deleteSession(long account, Jid address, int deviceId);

    @Query("DELETE FROM axolotl_session WHERE accountId=:account AND address=:address")
    public abstract void deleteSessions(long account, String address);

    @Query(
            "SELECT signedPreKeyRecord FROM axolotl_signed_pre_key WHERE accountId=:account AND"
                    + " removed=0")
    public abstract List<SignedPreKeyRecord> getSignedPreKeys(long account);

    @Query("SELECT preKeyRecord FROM axolotl_pre_key WHERE accountId=:account AND removed=0")
    public abstract List<PreKeyRecord> getPreKeys(long account);

    public void setSignedPreKey(Account account, int signedPreKeyId, SignedPreKeyRecord record) {
        insert(AxolotlSignedPreKeyEntity.of(account, signedPreKeyId, record));
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract void insert(AxolotlSignedPreKeyEntity signedPreKeyEntity);
}
