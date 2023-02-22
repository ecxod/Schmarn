package im.conversations.android.axolotl;

public class OutdatedSenderException extends AxolotlDecryptionException {

    public OutdatedSenderException(final String message) {
        super(message);
    }
}
