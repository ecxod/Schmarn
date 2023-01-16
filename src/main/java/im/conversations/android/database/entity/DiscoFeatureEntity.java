package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco_feature",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"discoId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"discoId"})})
public class DiscoFeatureEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long discoId;

    @NonNull public String feature;

    public static DiscoFeatureEntity of(final long discoId, final String feature) {
        final var entity = new DiscoFeatureEntity();
        entity.discoId = discoId;
        entity.feature = feature;
        return entity;
    }
}
