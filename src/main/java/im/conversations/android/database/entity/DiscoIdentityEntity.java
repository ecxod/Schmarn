package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco_identity",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"discoId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"discoId"})})
public class DiscoIdentityEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long discoId;

    public String category;
    public String type;
    public String name;
}
