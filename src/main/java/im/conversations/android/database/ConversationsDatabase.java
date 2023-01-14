package im.conversations.android.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import im.conversations.android.database.dao.AccountDao;
import im.conversations.android.database.dao.MessageDao;
import im.conversations.android.database.dao.PresenceDao;
import im.conversations.android.database.dao.RosterDao;
import im.conversations.android.database.entity.AccountEntity;
import im.conversations.android.database.entity.BlockedItemEntity;
import im.conversations.android.database.entity.ChatEntity;
import im.conversations.android.database.entity.DiscoEntity;
import im.conversations.android.database.entity.DiscoExtensionEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldEntity;
import im.conversations.android.database.entity.DiscoExtensionFieldValueEntity;
import im.conversations.android.database.entity.DiscoFeatureEntity;
import im.conversations.android.database.entity.DiscoIdentityEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessagePartEntity;
import im.conversations.android.database.entity.MessageVersionEntity;
import im.conversations.android.database.entity.PresenceEntity;
import im.conversations.android.database.entity.ReactionEntity;
import im.conversations.android.database.entity.RosterItemEntity;
import im.conversations.android.database.entity.RosterItemGroupEntity;

@Database(
        entities = {
            AccountEntity.class,
            BlockedItemEntity.class,
            ChatEntity.class,
            DiscoEntity.class,
            DiscoExtensionEntity.class,
            DiscoExtensionFieldEntity.class,
            DiscoExtensionFieldValueEntity.class,
            DiscoFeatureEntity.class,
            DiscoIdentityEntity.class,
            MessageEntity.class,
            MessagePartEntity.class,
            MessageVersionEntity.class,
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

    public abstract PresenceDao presenceDao();

    public abstract MessageDao messageDao();

    public abstract RosterDao rosterDao();
}
