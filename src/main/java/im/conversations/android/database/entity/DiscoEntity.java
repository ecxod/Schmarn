package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"accountId"})})
public class DiscoEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    public byte[] capsHash;
    public byte[] caps2Hash;
    public String caps2Algorithm;
    @NonNull Long accountId;
}
