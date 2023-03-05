package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.database.model.ChatIdentifier;
import im.conversations.android.database.model.ChatType;
import im.conversations.android.database.model.MucState;
import im.conversations.android.database.model.MucWithNick;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.muc.History;
import im.conversations.android.xmpp.model.muc.MultiUserChat;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.List;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiUserChatManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiUserChatManager.class);

    public MultiUserChatManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Integer> joinMultiUserChats() {
        LOGGER.info("joining multi user chats. start");
        return Futures.transform(
                getDatabase().chatDao().getMultiUserChats(getAccount().id, ChatType.MUC),
                this::joinMultiUserChats,
                MoreExecutors.directExecutor());
    }

    private int joinMultiUserChats(final List<MucWithNick> chats) {
        LOGGER.info("joining {} chats", chats.size());
        for (final MucWithNick chat : chats) {
            this.enterExisting(chat);
        }
        return chats.size();
    }

    public void enterExisting(final MucWithNick mucWithNick) {
        getDatabase().chatDao().setMucState(mucWithNick.chatId, MucState.JOINING);
        final var discoInfoFuture =
                getManager(DiscoManager.class).info(Entity.discoItem(mucWithNick.address));
        Futures.addCallback(
                discoInfoFuture,
                new ExistingMucJoiner(mucWithNick),
                MoreExecutors.directExecutor());
    }

    private void enterExisting(final MucWithNick mucWithNick, final InfoQuery infoQuery) {
        if (infoQuery.hasFeature(Namespace.MUC)
                && infoQuery.hasIdentityWithCategory("conference")) {
            sendJoinPresence(mucWithNick);
        } else {
            getDatabase().chatDao().setMucState(mucWithNick.chatId, MucState.NOT_A_MUC);
        }
    }

    private void sendJoinPresence(final MucWithNick mucWithNick) {
        final var nick = mucWithNick.nick();
        final Jid to;
        if (nick != null) {
            to = JidCreate.fullFrom(mucWithNick.address, nick);
        } else {
            to = JidCreate.fullFrom(mucWithNick.address, getAccount().fallbackNick());
        }
        final var presence = new Presence();
        presence.setTo(to);
        final var muc = presence.addExtension(new MultiUserChat());
        final var history = muc.addExtension(new History());
        history.setMaxChars(0);
        LOGGER.info("sending {} ", presence);
        connection.sendPresencePacket(presence);
    }

    public void handleSelfPresenceAvailable(final Presence presencePacket) {
        final MucUser mucUser = presencePacket.getExtension(MucUser.class);
        Preconditions.checkArgument(
                mucUser.getStatus().contains(MucUser.STATUS_CODE_SELF_PRESENCE));
        final var database = getDatabase();
        database.runInTransaction(
                () -> {
                    final ChatIdentifier chatIdentifier =
                            database.chatDao()
                                    .get(
                                            getAccount().id,
                                            presencePacket.getFrom().asBareJid(),
                                            ChatType.MUC);
                    if (chatIdentifier == null || chatIdentifier.archived) {
                        LOGGER.info(
                                "Available presence received for archived or non existent chat");
                        return;
                    }
                    database.chatDao()
                            .setMucState(
                                    chatIdentifier.id, MucState.AVAILABLE, mucUser.getStatus());
                });
    }

    public void handleSelfPresenceUnavailable(final Presence presencePacket) {
        final MucUser mucUser = presencePacket.getExtension(MucUser.class);
        Preconditions.checkArgument(
                mucUser.getStatus().contains(MucUser.STATUS_CODE_SELF_PRESENCE));
        final var database = getDatabase();
        database.runInTransaction(
                () -> {
                    final ChatIdentifier chatIdentifier =
                            database.chatDao()
                                    .get(
                                            getAccount().id,
                                            presencePacket.getFrom().asBareJid(),
                                            ChatType.MUC);
                    if (chatIdentifier == null) {
                        LOGGER.error("Unavailable presence received for non existent chat");
                    } else if (chatIdentifier.archived) {
                        database.chatDao().setMucState(chatIdentifier.id, null);
                    } else {
                        database.chatDao()
                                .setMucState(
                                        chatIdentifier.id,
                                        MucState.UNAVAILABLE,
                                        mucUser.getStatus());
                    }
                });
    }

    public void handleErrorPresence(final Presence presencePacket) {
        LOGGER.info("Received error presence from {}", presencePacket.getFrom());
        final var database = getDatabase();
        database.runInTransaction(
                () -> {
                    final ChatIdentifier chatIdentifier =
                            database.chatDao()
                                    .get(
                                            getAccount().id,
                                            presencePacket.getFrom().asBareJid(),
                                            ChatType.MUC);
                    if (chatIdentifier == null) {
                        // this is fine. error is simply not for a MUC
                        return;
                    }
                    final Error error = presencePacket.getError();
                    final Condition condition = error == null ? null : error.getCondition();
                    final String errorCondition = condition == null ? null : condition.getName();
                    database.chatDao()
                            .setMucState(
                                    chatIdentifier.id, MucState.ERROR_PRESENCE, errorCondition);
                });
    }

    private class ExistingMucJoiner implements FutureCallback<InfoQuery> {

        private final MucWithNick chat;

        private ExistingMucJoiner(final MucWithNick chat) {
            this.chat = chat;
        }

        @Override
        public void onSuccess(final InfoQuery result) {
            enterExisting(chat, result);
        }

        @Override
        public void onFailure(@NonNull final Throwable throwable) {
            final String errorCondition;
            if (throwable instanceof final IqErrorException iqErrorException) {
                final Error error = iqErrorException.getError();
                final Condition condition = error == null ? null : error.getCondition();
                errorCondition = condition == null ? null : condition.getName();
            } else {
                errorCondition = null;
            }
            getDatabase().chatDao().setMucState(chat.chatId, MucState.ERROR_IQ, errorCondition);
        }
    }
}
