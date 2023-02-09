package im.conversations.android.transformer;

import android.content.Context;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.oob.OutOfBandData;

public class Transformer {

    private final Context context;
    private final Account account;

    public Transformer(final Context context, final Account account) {
        this.context = context;
        this.account = account;
    }

    /**
     * @param transformation
     * @return returns true if there is something we want to send a delivery receipt for. Basically
     *     anything that created a new message in the database. Notably not something that only
     *     updated a status somewhere
     */
    public boolean transform(final Transformation transformation) {
        final var encrypted = transformation.getExtension(Encrypted.class);
        final var bodies = transformation.getExtensions(Body.class);
        final var outOfBandData = transformation.getExtensions(OutOfBandData.class);

        // TODO get or create Chat
        // TODO create MessageEntity or get existing entity
        // TODO for replaced message create a new version; re-target latestVersion
        // TODO apply errors, displayed, received etc
        // TODO apply reactions

        return true;
    }
}
