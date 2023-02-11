package im.conversations.android.database.model;

import com.google.common.base.Preconditions;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.transformer.Transformation;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.error.Text;
import im.conversations.android.xmpp.model.stanza.Message;

public class MessageState {

    public final Jid fromBare;

    public final String fromResource;

    public final StateType type;

    public final String errorCondition;

    public final String errorText;

    public MessageState(
            Jid fromBare,
            String fromResource,
            StateType type,
            String errorCondition,
            String errorText) {
        this.fromBare = fromBare;
        this.fromResource = fromResource;
        this.type = type;
        this.errorCondition = errorCondition;
        this.errorText = errorText;
    }

    public static MessageState error(final Transformation transformation) {
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

    public static MessageState delivered(final Transformation transformation) {
        return new MessageState(
                transformation.fromBare(),
                transformation.fromResource(),
                StateType.DELIVERED,
                null,
                null);
    }

    public static MessageState displayed(final Transformation transformation) {
        return new MessageState(
                transformation.fromBare(),
                transformation.fromResource(),
                StateType.DISPLAYED,
                null,
                null);
    }
}
