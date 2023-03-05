package im.conversations.android.database.model;

import androidx.room.Relation;
import com.google.common.collect.Iterables;
import im.conversations.android.database.entity.DiscoExtensionEntity;
import java.util.List;
import org.jxmpp.jid.Jid;

public class DiscoItemWithExtension {

    public Long discoId;
    public Jid address;

    @Relation(
            entity = DiscoExtensionEntity.class,
            parentColumn = "discoId",
            entityColumn = "discoId")
    public List<DiscoExtension> extensions;

    public DiscoExtension getExtension(final String type) {
        return Iterables.find(extensions, e -> type.equals(e.type), null);
    }
}
