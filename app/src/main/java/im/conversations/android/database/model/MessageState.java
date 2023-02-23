package im.conversations.android.database.model;

import com.google.common.base.Preconditions;
import im.conversations.android.transformer.MessageTransformation;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.error.Text;
import im.conversations.android.xmpp.model.stanza.Message;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.parts.Resourcepart;

public class MessageState {

    public final BareJid fromBare;

    public final Resourcepart fromResource;

    public final StateType type;

    public final String errorCondition;

    public final String errorText;

    public MessageState(
            BareJid fromBare,
            Resourcepart fromResource,
            StateType type,
            String errorCondition,
            String errorText) {
        this.fromBare = fromBare;
        this.fromResource = fromResource;
        this.type = type;
        this.errorCondition = errorCondition;
        this.errorText = errorText;
    }

    public static MessageState error(final MessageTransformation transformation) {
        Preconditions.checkArgument(transformation.type == Message.Type.ERROR);
        final Error error = transformation.getExtension(Error.class);
        final Condition condition = error == null ? null : error.getCondition();
        final Text text = error == null ? null : error.getText();
        return new MessageState(
                transformation.fromBare(),
                transformation.fromResource(),
                StateType.ERROR,
                condition == null ? null : condition.getName(),
                text == null ? null : text.getContent());
    }

    public static MessageState delivered(final MessageTransformation transformation) {
        return new MessageState(
                transformation.fromBare(),
                transformation.fromResource(),
                StateType.DELIVERED,
                null,
                null);
    }

    public static MessageState displayed(final MessageTransformation transformation) {
        return new MessageState(
                transformation.fromBare(),
                transformation.fromResource(),
                StateType.DISPLAYED,
                null,
                null);
    }
}
