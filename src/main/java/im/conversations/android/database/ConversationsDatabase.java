package im.conversations.android.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import im.conversations.android.database.dao.AccountDao;
import im.conversations.android.database.dao.AvatarDao;
import im.conversations.android.database.dao.AxolotlDao;
import im.conversations.android.database.dao.BlockingDao;
import im.conversations.android.database.dao.BookmarkDao;
import im.conversations.android.database.dao.ChatDao;
import im.conversations.android.database.dao.DiscoDao;
import im.conversations.android.database.dao.MessageDao;
import im.conversations.android.database.dao.NickDao;
import im.conversations.android.database.dao.PresenceDao;
import im.conversations.android.database.dao.RosterDao;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.entity.AvatarAdditionalEntity;
import im.conversations.android.database.entity.AvatarEntity;
import im.conversations.android.database.entity.AxolotlDeviceListEntity;
import im.conversations.android.database.entity.AxolotlDeviceListItemEntity;
import im.conversations.android.database.entity.AxolotlIdentityEntity;
import im.conversations.android.database.entity.AxolotlIdentityKeyPairEntity;
import im.conversations.android.database.entity.AxolotlPreKeyEntity;
import im.conversations.android.database.entity.AxolotlSessionEntity;
import im.conversations.android.database.entity.AxolotlSignedPreKeyEntity;
import im.conversations.android.database.entity.BlockedItemEntity;
import im.conversations.android.database.entity.BookmarkEntity;
import im.conversations.android.database.entity.ChatEntity;
import im.conversations.android.database.entity.DiscoEntity;
import im.conversations.android.database.entity.DiscoExtensionEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldValueEntity;
import im.conversations.android.database.entity.DiscoFeatureEntity;
import im.conversations.android.database.entity.DiscoIdentityEntity;
import im.conversations.android.database.entity.DiscoItemEntity;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.entity.NickEntity;
import im.conversations.android.database.entity.PresenceEntity;
import im.conversations.android.database.entity.ReactionEntity;
import im.conversations.android.database.entity.RosterItemEntity;
import im.conversations.android.database.entity.RosterItemGroupEntity;

@Database(
        entities = {
            AccountEntity.class,
            AvatarAdditionalEntity.class,
            AvatarEntity.class,
            AxolotlDeviceListEntity.class,
            AxolotlDeviceListItemEntity.class,
            AxolotlIdentityEntity.class,
            AxolotlIdentityKeyPairEntity.class,
            AxolotlPreKeyEntity.class,
            AxolotlSessionEntity.class,
            AxolotlSignedPreKeyEntity.class,
            BlockedItemEntity.class,
            BookmarkEntity.class,
            ChatEntity.class,
            DiscoEntity.class,
            DiscoExtensionEntity.class,
            DiscoExtensionFieldEntity.class,
            DiscoExtensionFieldValueEntity.class,
            DiscoFeatureEntity.class,
            DiscoIdentityEntity.class,
            DiscoItemEntity.class,
            MessageEntity.class,
            MessageContentEntity.class,
            MessageVersionEntity.class,
            NickEntity.class,
            PresenceEntity.class,
            ReactionEntity.class,
            RosterItemEntity.class,
            RosterItemGroupEntity.class
        },
        version = 1)
@TypeConverters(Converters.class)
public abstract class ConversationsDatabase extends RoomDatabase {

    private static volatile ConversationsDatabase INSTANCE = null;

    public static ConversationsDatabase getInstance(final Context context) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (ConversationsDatabase.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            final Context application = context.getApplicationContext();
            INSTANCE =
                    Room.databaseBuilder(application, ConversationsDatabase.class, "conversations")
                            .build();
            return INSTANCE;
        }
    }

    public abstract AccountDao accountDao();

    public abstract AvatarDao avatarDao();

    public abstract AxolotlDao axolotlDao();

    public abstract BlockingDao blockingDao();

    public abstract BookmarkDao bookmarkDao();

    public abstract ChatDao chatDao();

    public abstract DiscoDao discoDao();

    public abstract MessageDao messageDao();

    public abstract NickDao nickDao();

    public abstract PresenceDao presenceDao();

    public abstract RosterDao rosterDao();
}
