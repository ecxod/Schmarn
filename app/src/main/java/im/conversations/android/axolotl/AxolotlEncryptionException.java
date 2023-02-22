package im.conversations.android.axolotl;

public class AxolotlEncryptionException extends Exception {

    public AxolotlEncryptionException(String msg) {
        super(msg);
    }

    public AxolotlEncryptionException(String msg, Exception e) {
        super(msg, e);
    }

    public AxolotlEncryptionException(Exception e) {
        super(e);
    }
}
