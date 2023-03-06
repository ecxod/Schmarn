package im.conversations.android.database.model;

import androidx.room.Relation;
import com.google.common.collect.Iterables;
import im.conversations.android.database.entity.MessageContentEntity;
import java.time.Instant;
import java.util.List;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

public class ChatOverviewItem {

    public long id;
    public long accountId;
    public String address;
    public ChatType type;

    public Instant sentAt;

    public boolean outgoing;

    public Jid toBare;
    public String toResource;
    public Jid fromBare;
    public String fromResource;
    public long version;

    public String rosterName;
    public String nick;
    public String discoIdentityName;
    public String bookmarkName;

    public String vCardPhoto;
    public String avatar;

    public int unread;

    @Relation(
            entity = MessageContentEntity.class,
            parentColumn = "version",
            entityColumn = "messageVersionId")
    public List<MessageContent> contents;

    public String name() {
        return switch (type) {
            case MUC -> mucName();
            case INDIVIDUAL -> individualName();
            default -> address;
        };
    }

    public String message() {
        final var firstMessageContent = Iterables.getFirst(contents, null);
        return firstMessageContent == null ? null : firstMessageContent.body;
    }

    public Sender getSender() {
        if (outgoing) {
            return new SenderYou();
        } else if (type == ChatType.MUC) {
            if (fromResource != null) {
                return new SenderName(fromResource);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private String individualName() {
        if (notNullNotEmpty(rosterName)) {
            return rosterName.trim();
        }
        if (notNullNotEmpty(nick)) {
            return nick.trim();
        }
        return fallbackName();
    }

    private String fallbackName() {
        final Jid jid = getJidAddress();
        if (jid == null) {
            return this.address;
        }
        if (jid.hasLocalpart()) {
            return jid.getLocalpartOrThrow().toString();
        } else {
            return jid.toString();
        }
    }

    private String mucName() {
        if (notNullNotEmpty(this.bookmarkName)) {
            return this.bookmarkName.trim();
        }
        if (notNullNotEmpty(this.discoIdentityName)) {
            return this.discoIdentityName.trim();
        }
        return fallbackName();
    }

    private Jid getJidAddress() {
        return address == null ? null : JidCreate.fromOrNull(address);
    }

    private static boolean notNullNotEmpty(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    public sealed interface Sender permits SenderYou, SenderName {}

    public static final class SenderYou implements Sender {}

    public static final class SenderName implements Sender {
        public final String name;

        public SenderName(String name) {
            this.name = name;
        }
    }
}
