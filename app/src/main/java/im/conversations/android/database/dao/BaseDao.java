package im.conversations.android.database.dao;

import androidx.room.Query;
import org.jxmpp.jid.Jid;

public abstract class BaseDao {

    @Query(
            "SELECT EXISTS (SELECT disco_item.id FROM disco_item JOIN disco_feature on"
                    + " disco_item.discoId=disco_feature.discoId WHERE accountId=:account AND"
                    + " address=:entity AND feature=:feature)")
    protected abstract boolean hasDiscoItemFeature(
            final long account, final Jid entity, final String feature);
}
