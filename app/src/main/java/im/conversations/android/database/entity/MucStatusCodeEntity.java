package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.common.collect.Collections2;
import java.util.Collection;

@Entity(
        tableName = "muc_status_code",
        foreignKeys = {
            @ForeignKey(
                    entity = ChatEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"chatId"},
                    onDelete = ForeignKey.CASCADE)
        },
        indices = {@Index(value = "chatId")})
public class MucStatusCodeEntity {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long chatId;

    @NonNull public Integer code;

    public static Collection<MucStatusCodeEntity> of(
            final long chatId, final Collection<Integer> codes) {
        return Collections2.transform(codes, c -> of(chatId, c));
    }

    private static MucStatusCodeEntity of(final long chatId, final int code) {
        final var entity = new MucStatusCodeEntity();
        entity.chatId = chatId;
        entity.code = code;
        return entity;
    }
}
