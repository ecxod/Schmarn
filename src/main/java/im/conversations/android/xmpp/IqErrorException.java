package im.conversations.android.xmpp;

import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.stanza.Iq;

public class IqErrorException extends Exception {

    private final Iq response;

    public IqErrorException(Iq response) {
        super(getErrorText(response));
        this.response = response;
    }

    public Error getError() {
        return this.response.getError();
    }

    private static String getErrorText(final Iq response) {
        final var error = response.getError();
        final var text = error == null ? null : error.getText();
        return text == null ? null : text.getContent();
    }

    public Iq getResponse() {
        return this.response;
    }
}
