package im.conversations.android.database.model;

public enum Modification {
    ORIGINAL,
    EDIT, // XEP-0308: Last Message Correction
    RETRACTION, // XEP-0424: Message Retraction
    MODERATION // XEP-0425: Message Moderation
}
