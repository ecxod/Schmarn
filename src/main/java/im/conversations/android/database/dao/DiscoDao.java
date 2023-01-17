package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Upsert;
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
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.data.Field;
import im.conversations.android.xmpp.model.data.Value;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import java.util.Collection;

@Dao
public abstract class DiscoDao {

    @Upsert(entity = DiscoItemEntity.class)
    protected abstract void insertDiscoItems(Collection<DiscoItemWithParent> items);

    @Insert
    protected abstract void insertDiscoIdentities(Collection<DiscoIdentityEntity> identities);

    @Insert
    protected abstract void insertDiscoFeatures(Collection<DiscoFeatureEntity> features);

    @Insert
    protected abstract void insertDiscoFieldValues(
            Collection<DiscoExtensionFieldValueEntity> value);

    @Upsert(entity = DiscoItemEntity.class)
    protected abstract void insert(DiscoItemWithDiscoId item);

    @Insert
    protected abstract long insert(DiscoEntity entity);

    @Insert
    protected abstract long insert(DiscoExtensionEntity entity);

    @Insert
    protected abstract long insert(DiscoExtensionFieldEntity entity);

    @Transaction
    public void set(final Account account, final Jid parent, final Collection<Item> items) {
        final var entities =
                Collections2.transform(items, i -> DiscoItemWithParent.of(account.id, parent, i));
        insertDiscoItems(entities);
    }

    @Transaction
    public boolean set(
            final Account account,
            final Jid address,
            final String node,
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
        insert(DiscoItemWithDiscoId.of(account.id, address, node, existingDiscoId));
        return true;
    }

    @Transaction
    public void set(
            final Account account,
            final Jid address,
            final String node,
            final byte[] capsHash,
            final byte[] caps2HashSha256,
            final InfoQuery infoQuery) {

        final Long existingDiscoId = getDiscoId(account.id, caps2HashSha256);
        if (existingDiscoId != null) {
            insert(DiscoItemWithDiscoId.of(account.id, address, node, existingDiscoId));
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
            final var extensionId = insert(DiscoExtensionEntity.of(discoId));
            for (final var field : data.getExtensions(Field.class)) {
                final var fieldId =
                        insert(DiscoExtensionFieldEntity.of(extensionId, field.getFieldName()));
                insertDiscoFieldValues(
                        Collections2.transform(
                                field.getExtensions(Value.class),
                                v -> DiscoExtensionFieldValueEntity.of(fieldId, v.getContent())));
            }
        }
    }

    @Query("SELECT id FROM disco WHERE accountId=:accountId AND caps2HashSha256=:caps2HashSha256")
    protected abstract Long getDiscoId(final long accountId, final byte[] caps2HashSha256);

    @Query("SELECT id FROM disco WHERE accountId=:accountId AND capsHash=:capsHash")
    protected abstract Long getDiscoIdByCapsHash(final long accountId, final byte[] capsHash);

    public static class DiscoItemWithParent {
        public long accountId;
        public Jid address;
        public String node;
        public Jid parent;

        public static DiscoItemWithParent of(
                final long account, final Jid parent, final Item item) {
            final var entity = new DiscoItemWithParent();
            entity.accountId = account;
            entity.address = item.getJid();
            entity.node = item.getNode();
            entity.parent = parent;
            return entity;
        }
    }

    public static class DiscoItemWithDiscoId {
        public long accountId;
        public Jid address;
        public String node;
        public long discoId;

        public static DiscoItemWithDiscoId of(
                final long account, final Jid address, final String node, final long discoId) {
            final var entity = new DiscoItemWithDiscoId();
            entity.accountId = account;
            entity.address = address;
            entity.node = node;
            entity.discoId = discoId;
            return entity;
        }
    }
}
