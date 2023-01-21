package im.conversations.android.database.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.DiscoEntity;
import im.conversations.android.database.entity.DiscoExtensionEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldValueEntity;
import im.conversations.android.database.entity.DiscoFeatureEntity;
import im.conversations.android.database.entity.DiscoIdentityEntity;
import im.conversations.android.database.entity.DiscoItemEntity;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.data.Value;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import java.util.Collection;

@Dao
public abstract class DiscoDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract void insertDiscoItems(Collection<DiscoItemEntity> items);

    @Insert
    protected abstract void insert(DiscoItemEntity item);

    @Insert
    protected abstract void insertDiscoIdentities(Collection<DiscoIdentityEntity> identities);

    @Insert
    protected abstract void insertDiscoFeatures(Collection<DiscoFeatureEntity> features);

    @Query(
            "DELETE FROM disco_item WHERE accountId=:account AND parentAddress=:parent AND"
                    + " parentNode=:parentNode AND address NOT IN(:existent)")
    protected abstract void deleteNonExistentDiscoItems(
            final long account,
            final Jid parent,
            final String parentNode,
            final Collection<Jid> existent);

    @Query(
            "UPDATE presence SET discoId=:discoId WHERE accountId=:account AND address=:address"
                    + " AND resource=:resource")
    protected abstract void updateDiscoIdInPresence(
            long account, Jid address, String resource, long discoId);

    @Query(
            "UPDATE disco_item SET discoId=:discoId WHERE accountId=:account AND address=:address"
                    + " AND node=:node")
    protected abstract int updateDiscoIdInDiscoItem(
            long account, Jid address, String node, long discoId);

    @Insert
    protected abstract void insertDiscoFieldValues(
            Collection<DiscoExtensionFieldValueEntity> value);

    @Insert
    protected abstract long insert(DiscoEntity entity);

    @Insert
    protected abstract long insert(DiscoExtensionEntity entity);

    @Insert
    protected abstract long insert(DiscoExtensionFieldEntity entity);

    @Transaction
    public void set(
            final Account account,
            final Entity.DiscoItem parent,
            final String parentNode,
            final Collection<Item> items) {
        final var entities =
                Collections2.transform(
                        items, i -> DiscoItemEntity.of(account.id, parent.address, parentNode, i));
        insertDiscoItems(entities);
        deleteNonExistentDiscoItems(
                account.id,
                parent.address,
                Strings.nullToEmpty(parentNode),
                Collections2.transform(items, Item::getJid));
    }

    @Transaction
    public boolean set(
            final Account account,
            final Entity entity,
            @Nullable final String node,
            final EntityCapabilities.Hash capsHash) {
        final Long existingDiscoId;
        if (capsHash instanceof EntityCapabilities2.EntityCaps2Hash) {
            existingDiscoId = getDiscoId(account.id, capsHash.hash);
        } else if (capsHash instanceof EntityCapabilities.EntityCapsHash) {
            existingDiscoId = getDiscoIdByCapsHash(account.id, capsHash.hash);
        } else {
            existingDiscoId = null;
        }
        if (existingDiscoId == null) {
            return false;
        }
        updateDiscoId(account.id, entity, node, existingDiscoId);
        return true;
    }

    protected void updateDiscoId(
            final long account,
            final Entity entity,
            @Nullable final String node,
            final long discoId) {
        if (entity instanceof Entity.DiscoItem) {
            if (updateDiscoIdInDiscoItem(
                            account, entity.address, Strings.nullToEmpty(node), discoId)
                    > 0) {
                return;
            }
            insert(DiscoItemEntity.of(account, entity.address, node, discoId));
        } else if (entity instanceof Entity.Presence) {
            updateDiscoIdInPresence(
                    account,
                    entity.address.asBareJid(),
                    Strings.nullToEmpty(entity.address.getResource()),
                    discoId);
        }
    }

    @Transaction
    public void set(
            final Account account,
            final Entity entity,
            final String node,
            final byte[] capsHash,
            final byte[] caps2HashSha256,
            final InfoQuery infoQuery) {

        final Long existingDiscoId = getDiscoId(account.id, caps2HashSha256);
        if (existingDiscoId != null) {
            updateDiscoId(account.id, entity, node, existingDiscoId);
            return;
        }
        final long discoId = insert(DiscoEntity.of(account.id, capsHash, caps2HashSha256));

        insertDiscoIdentities(
                Collections2.transform(
                        infoQuery.getExtensions(Identity.class),
                        i -> DiscoIdentityEntity.of(discoId, i)));

        insertDiscoFeatures(
                Collections2.transform(
                        infoQuery.getExtensions(Feature.class),
                        f -> DiscoFeatureEntity.of(discoId, f.getVar())));
        for (final Data data : infoQuery.getExtensions(Data.class)) {
            final var extensionId = insert(DiscoExtensionEntity.of(discoId, data.getFormType()));
            for (final var field : data.getFields()) {
                final var fieldId =
                        insert(DiscoExtensionFieldEntity.of(extensionId, field.getFieldName()));
                insertDiscoFieldValues(
                        Collections2.transform(
                                field.getExtensions(Value.class),
                                v -> DiscoExtensionFieldValueEntity.of(fieldId, v.getContent())));
            }
        }
        updateDiscoId(account.id, entity, node, discoId);
    }

    @Query("SELECT id FROM disco WHERE accountId=:accountId AND caps2HashSha256=:caps2HashSha256")
    protected abstract Long getDiscoId(final long accountId, final byte[] caps2HashSha256);

    @Query("SELECT id FROM disco WHERE accountId=:accountId AND capsHash=:capsHash")
    protected abstract Long getDiscoIdByCapsHash(final long accountId, final byte[] capsHash);

    @Query(
            "SELECT EXISTS (SELECT disco_item.id FROM disco_item JOIN disco_feature on"
                    + " disco_item.discoId=disco_feature.discoId WHERE accountId=:account AND"
                    + " address=:entity AND feature=:feature)")
    public abstract boolean hasFeature(final long account, final Jid entity, final String feature);
}
