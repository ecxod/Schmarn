package im.conversations.android.axolotl;

public class AxolotlDecryptionException extends Exception {

    public AxolotlDecryptionException(final String message) {
        super(message);
    }

    public AxolotlDecryptionException(final Throwable throwable) {
        super(throwable);
    }
}
