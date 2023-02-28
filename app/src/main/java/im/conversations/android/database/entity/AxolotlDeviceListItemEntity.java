package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "axolotl_device_list_item",
        foreignKeys =
                @ForeignKey(
                        entity = AxolotlDeviceListEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"deviceListId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(value = {"deviceListId"}),
            @Index(
                    value = {"deviceListId", "deviceId"},
                    unique = true)
        })
public class AxolotlDeviceListItemEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long deviceListId;

    public Integer deviceId;

    public boolean confirmedInPep;

    public static AxolotlDeviceListItemEntity of(
            final long deviceListId, final int deviceId, final boolean confirmedInPep) {
        final var entity = new AxolotlDeviceListItemEntity();
        entity.deviceListId = deviceListId;
        entity.deviceId = deviceId;
        entity.confirmedInPep = confirmedInPep;
        return entity;
    }
}
