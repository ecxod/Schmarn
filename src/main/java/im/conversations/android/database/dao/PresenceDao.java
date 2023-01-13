package im.conversations.android.database.dao;

import androidx.room.Dao;
import androidx.room.Query;

@Dao
public interface PresenceDao {

    @Query("DELETE FROM presence WHERE accountId=:account")
    void deletePresences(long account);
}
