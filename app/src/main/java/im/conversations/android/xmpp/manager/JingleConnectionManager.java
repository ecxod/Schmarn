package im.conversations.android.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.services.AppRTCAudioManager;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import eu.siacs.conversations.xmpp.jingle.ToneManager;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import im.conversations.android.IDs;
import im.conversations.android.database.model.Account;
import im.conversations.android.notification.RtpSessionNotification;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.jmi.Accept;
import im.conversations.android.xmpp.model.jmi.JingleMessage;
import im.conversations.android.xmpp.model.jmi.Proceed;
import im.conversations.android.xmpp.model.jmi.Propose;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JingleConnectionManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(JingleConnectionManager.class);

    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE =
            Executors.newSingleThreadScheduledExecutor();
    private final HashMap<RtpSessionProposal, DeviceDiscoveryState> rtpSessionProposals =
            new HashMap<>();
    private final ConcurrentHashMap<AbstractJingleConnection.Id, AbstractJingleConnection>
            connections = new ConcurrentHashMap<>();

    private final Cache<PersistableSessionId, TerminatedRtpSession> terminatedSessions =
            CacheBuilder.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build();

    private final RtpSessionNotification rtpSessionNotification;

    private OnJingleRtpConnectionUpdate onJingleRtpConnectionUpdate;

    public JingleConnectionManager(final Context context, final XmppConnection connection) {
        super(context, connection);
        this.rtpSessionNotification = new RtpSessionNotification(context);
    }

    @Override
    public Account getAccount() {
        return super.getAccount();
    }

    public void handleJingle(final Iq iq) {
        final JinglePacket packet = JinglePacket.upgrade(iq);
        final String sessionId = packet.getSessionId();
        if (sessionId == null) {
            respondWithJingleError(
                    iq, "unknown-session", Error.Type.CANCEL, new Condition.ItemNotFound());
            return;
        }
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(packet);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection != null) {
            existingJingleConnection.deliverPacket(packet);
        } else if (packet.getAction() == JinglePacket.Action.SESSION_INITIATE) {
            final Jid from = packet.getFrom();
            final Content content = packet.getJingleContent();
            final String descriptionNamespace =
                    content == null ? null : content.getDescriptionNamespace();
            final AbstractJingleConnection connection;
            if (Namespace.JINGLE_APPS_RTP.equals(descriptionNamespace) && isUsingClearNet()) {
                final boolean sessionEnded =
                        this.terminatedSessions.asMap().containsKey(PersistableSessionId.of(id));
                final boolean stranger = isWithStrangerAndStrangerNotificationsAreOff(id.with);
                if (isBusy() || sessionEnded || stranger) {
                    LOGGER.debug(
                            this.connection.getAccount().address
                                    + ": rejected session with "
                                    + id.with
                                    + " because busy. sessionEnded="
                                    + sessionEnded
                                    + ", stranger="
                                    + stranger);
                    this.connection.sendResultFor(packet);
                    final JinglePacket sessionTermination =
                            new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
                    sessionTermination.setTo(id.with);
                    sessionTermination.setReason(Reason.BUSY, null);
                    this.connection.sendIqPacket(sessionTermination, null);
                    return;
                }
                connection = new JingleRtpConnection(context, this.connection, id, from);
            } else {
                respondWithJingleError(
                        packet,
                        "unsupported-info",
                        Error.Type.CANCEL,
                        new Condition.FeatureNotImplemented());
                return;
            }
            connections.put(id, connection);
            connection.deliverPacket(packet);
        } else {
            Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
            respondWithJingleError(
                    packet, "unknown-session", Error.Type.CANCEL, new Condition.ItemNotFound());
        }
    }

    private boolean isUsingClearNet() {
        // todo bring back proper Tor check
        return !connection.getAccount().isOnion();
    }

    public boolean isBusy() {
        // TODO check if in actual phone call
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                if (((JingleRtpConnection) connection).isTerminated()) {
                    continue;
                }
                return true;
            }
        }
        synchronized (this.rtpSessionProposals) {
            return this.rtpSessionProposals.containsValue(DeviceDiscoveryState.DISCOVERED)
                    || this.rtpSessionProposals.containsValue(DeviceDiscoveryState.SEARCHING)
                    || this.rtpSessionProposals.containsValue(
                            JingleConnectionManager.DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED);
        }
    }

    public void notifyPhoneCallStarted() {
        for (AbstractJingleConnection connection : connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                final JingleRtpConnection rtpConnection = (JingleRtpConnection) connection;
                if (rtpConnection.isTerminated()) {
                    continue;
                }
                rtpConnection.notifyPhoneCall();
            }
        }
    }

    private Optional<RtpSessionProposal> findMatchingSessionProposal(
            final Jid with, final Set<Media> media) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                final DeviceDiscoveryState state = entry.getValue();
                final boolean openProposal =
                        state == DeviceDiscoveryState.DISCOVERED
                                || state == DeviceDiscoveryState.SEARCHING
                                || state == DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED;
                if (openProposal
                        && proposal.with.equals(with.asBareJid())
                        && proposal.media.equals(media)) {
                    return Optional.of(proposal);
                }
            }
        }
        return Optional.absent();
    }

    private boolean hasMatchingRtpSession(final Jid with, final Set<Media> media) {
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                final JingleRtpConnection rtpConnection = (JingleRtpConnection) connection;
                if (rtpConnection.isTerminated()) {
                    continue;
                }
                if (rtpConnection.getId().with.asBareJid().equals(with.asBareJid())
                        && rtpConnection.getMedia().equals(media)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWithStrangerAndStrangerNotificationsAreOff(Jid with) {
        final boolean notifyForStrangers = rtpSessionNotification.notificationsFromStrangers();
        if (notifyForStrangers) {
            return false;
        }
        return getDatabase().rosterDao().isInRoster(getAccount().id, with.asBareJid());
    }

    public ScheduledFuture<?> schedule(
            final Runnable runnable, final long delay, final TimeUnit timeUnit) {
        return SCHEDULED_EXECUTOR_SERVICE.schedule(runnable, delay, timeUnit);
    }

    private void respondWithJingleError(
            final Iq original, String jingleCondition, final Error.Type type, Condition condition) {
        // TODO add jingle condition
        connection.sendErrorFor(original, type, condition);
    }

    public void handle(final Message message) {
        final String id = message.getId();
        final String stanzaId = getManager(StanzaIdManager.class).getStanzaId(message);
        final JingleMessage jingleMessage = message.getExtension(JingleMessage.class);
        this.deliverMessage(message.getTo(), message.getFrom(), jingleMessage, id, stanzaId);
    }

    private void deliverMessage(
            final Jid to,
            final Jid from,
            final JingleMessage message,
            final String remoteMsgId,
            final String serverMsgId) {
        final String sessionId = message.getSessionId();
        if (Strings.isNullOrEmpty(sessionId)) {
            return;
        }
        if (message instanceof Accept) {
            for (AbstractJingleConnection connection : connections.values()) {
                if (connection instanceof JingleRtpConnection) {
                    final JingleRtpConnection rtpConnection = (JingleRtpConnection) connection;
                    final AbstractJingleConnection.Id id = connection.getId();
                    if (id.sessionId.equals(sessionId)) {
                        rtpConnection.deliveryMessage(from, message, serverMsgId);
                        return;
                    }
                }
            }
            return;
        }
        final boolean fromSelf = from.asBareJid().equals(connection.getBoundAddress().asBareJid());
        final boolean addressedDirectly = to != null && to.equals(connection.getBoundAddress());
        final AbstractJingleConnection.Id id;
        if (fromSelf) {
            if (to != null && to.hasResource()) {
                id = AbstractJingleConnection.Id.of(to, sessionId);
            } else {
                return;
            }
        } else {
            id = AbstractJingleConnection.Id.of(from, sessionId);
        }
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection != null) {
            if (existingJingleConnection instanceof JingleRtpConnection) {
                ((JingleRtpConnection) existingJingleConnection)
                        .deliveryMessage(from, message, serverMsgId);
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": "
                                + existingJingleConnection.getClass().getName()
                                + " does not support jingle messages");
            }
            return;
        }

        if (fromSelf) {
            if (message instanceof Proceed) {

                // if we've previously rejected a call because we were busy (which would have
                // created a CallLogEntry) but that call was picked up on another one of our devices
                // we want to update that CallLogEntry to say picked up (not missed)

                /*final Conversation c =
                        mXmppConnectionService.findOrCreateConversation(
                                account, id.with, false, false);
                final Message previousBusy = c.findRtpSession(sessionId, Message.STATUS_RECEIVED);
                if (previousBusy != null) {
                    previousBusy.setBody(new RtpSessionStatus(true, 0).toString());
                    if (serverMsgId != null) {
                        previousBusy.setServerMsgId(serverMsgId);
                    }
                    previousBusy.setTime(timestamp);
                    mXmppConnectionService.updateMessage(previousBusy, true);
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": updated previous busy because call got picked up by another device");
                    return;
                }*/
            }
            // TODO handle reject for cases where we don’t have carbon copies (normally reject is to
            // be sent to own bare jid as well)
            LOGGER.debug(connection.getAccount().address + ": ignore jingle message from self");
            return;
        }

        if (message instanceof Propose) {
            final Propose propose = (Propose) message;
            final List<GenericDescription> descriptions = propose.getDescriptions();
            final Collection<RtpDescription> rtpDescriptions =
                    Collections2.transform(
                            Collections2.filter(descriptions, d -> d instanceof RtpDescription),
                            input -> (RtpDescription) input);
            if (rtpDescriptions.size() > 0
                    && rtpDescriptions.size() == descriptions.size()
                    && isUsingClearNet()) {
                final Collection<Media> media =
                        Collections2.transform(rtpDescriptions, RtpDescription::getMedia);
                if (media.contains(Media.UNKNOWN)) {
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": encountered unknown media in session proposal. "
                                    + propose);
                    return;
                }
                final Optional<RtpSessionProposal> matchingSessionProposal =
                        findMatchingSessionProposal(id.with, ImmutableSet.copyOf(media));
                if (matchingSessionProposal.isPresent()) {
                    final String ourSessionId = matchingSessionProposal.get().sessionId;
                    final String theirSessionId = id.sessionId;
                    if (ComparisonChain.start()
                                    .compare(ourSessionId, theirSessionId)
                                    .compare(
                                            connection.getBoundAddress().toString(),
                                            id.with.toString())
                                    .result()
                            > 0) {
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": our session lost tie break. automatically accepting"
                                        + " their session. winning Session="
                                        + theirSessionId);
                        // TODO a retract for this reason should probably include some indication of
                        // tie break
                        retractSessionProposal(matchingSessionProposal.get());
                        final JingleRtpConnection rtpConnection =
                                new JingleRtpConnection(context, this.connection, id, from);
                        this.connections.put(id, rtpConnection);
                        rtpConnection.setProposedMedia(ImmutableSet.copyOf(media));
                        rtpConnection.deliveryMessage(from, message, serverMsgId);
                    } else {
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": our session won tie break. waiting for other party to"
                                        + " accept. winningSession="
                                        + ourSessionId);
                    }
                    return;
                }
                final boolean stranger = isWithStrangerAndStrangerNotificationsAreOff(id.with);
                if (isBusy() || stranger) {
                    writeLogMissedIncoming(id.with.asBareJid(), id.sessionId, serverMsgId);
                    if (stranger) {
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": ignoring call proposal from stranger "
                                        + id.with);
                        return;
                    }
                    final int activeDevices =
                            getDatabase()
                                    .discoDao()
                                    .countPresencesWithFeature(
                                            getAccount(), Namespace.JINGLE_APPS_RTP);
                    Log.d(Config.LOGTAG, "active devices with rtp capability: " + activeDevices);
                    if (activeDevices == 0) {
                        final Message reject = MessageGenerator.sessionReject(from, sessionId);
                        connection.sendMessagePacket(reject);
                    } else {
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": ignoring proposal because busy on this device but"
                                        + " there are other devices");
                    }
                } else {
                    final JingleRtpConnection rtpConnection =
                            new JingleRtpConnection(context, this.connection, id, from);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.setProposedMedia(ImmutableSet.copyOf(media));
                    rtpConnection.deliveryMessage(from, message, serverMsgId);
                }
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": unable to react to proposed session with "
                                + rtpDescriptions.size()
                                + " rtp descriptions of "
                                + descriptions.size()
                                + " total descriptions");
            }
        } else if (addressedDirectly && "proceed".equals(message.getName())) {
            synchronized (rtpSessionProposals) {
                final RtpSessionProposal proposal =
                        getRtpSessionProposal(from.asBareJid(), sessionId);
                if (proposal != null) {
                    rtpSessionProposals.remove(proposal);
                    final JingleRtpConnection rtpConnection =
                            new JingleRtpConnection(
                                    context,
                                    this.connection,
                                    id,
                                    this.connection.getBoundAddress());
                    rtpConnection.setProposedMedia(proposal.media);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.transitionOrThrow(AbstractJingleConnection.State.PROPOSED);
                    rtpConnection.deliveryMessage(from, message, serverMsgId);
                } else {
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": no rtp session proposal found for "
                                    + from
                                    + " to deliver proceed");
                    if (remoteMsgId == null) {
                        return;
                    }
                    final Message errorMessage = new Message();
                    errorMessage.setTo(from);
                    errorMessage.setId(remoteMsgId);
                    errorMessage.setType(Message.Type.ERROR);
                    final Element error = errorMessage.addChild("error");
                    error.setAttribute("code", "404");
                    error.setAttribute("type", "cancel");
                    error.addChild("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas");
                    connection.sendMessagePacket(errorMessage);
                }
            }
        } else if (addressedDirectly && "reject".equals(message.getName())) {
            final RtpSessionProposal proposal = getRtpSessionProposal(from.asBareJid(), sessionId);
            synchronized (rtpSessionProposals) {
                if (proposal != null && rtpSessionProposals.remove(proposal) != null) {
                    writeLogMissedOutgoing(proposal.with, proposal.sessionId, serverMsgId);
                    ToneManager.getInstance(context)
                            .transition(RtpEndUserState.DECLINED_OR_BUSY, proposal.media);
                    notifyJingleRtpConnectionUpdate(
                            proposal.with, proposal.sessionId, RtpEndUserState.DECLINED_OR_BUSY);
                } else {
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": no rtp session proposal found for "
                                    + from
                                    + " to deliver reject");
                }
            }
        } else {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": retrieved out of order jingle message"
                            + message);
        }
    }

    private RtpSessionProposal getRtpSessionProposal(Jid from, String sessionId) {
        for (RtpSessionProposal rtpSessionProposal : rtpSessionProposals.keySet()) {
            if (rtpSessionProposal.sessionId.equals(sessionId)
                    && rtpSessionProposal.with.equals(from)) {
                return rtpSessionProposal;
            }
        }
        return null;
    }

    private void writeLogMissedOutgoing(Jid with, final String sessionId, String serverMsgId) {}

    private void writeLogMissedIncoming(Jid with, final String sessionId, String serverMsgId) {}

    public Optional<OngoingRtpSession> getOngoingRtpConnection(final Jid contact) {
        for (final Map.Entry<AbstractJingleConnection.Id, AbstractJingleConnection> entry :
                this.connections.entrySet()) {
            if (entry.getValue() instanceof JingleRtpConnection) {
                final AbstractJingleConnection.Id id = entry.getKey();
                if (id.with.asBareJid().equals(contact.asBareJid())) {
                    return Optional.of(id);
                }
            }
        }
        synchronized (this.rtpSessionProposals) {
            for (final Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (contact.asBareJid().equals(proposal.with)) {
                    final DeviceDiscoveryState preexistingState = entry.getValue();
                    if (preexistingState != null
                            && preexistingState != DeviceDiscoveryState.FAILED) {
                        return Optional.of(proposal);
                    }
                }
            }
        }
        return Optional.absent();
    }

    void finishConnection(final AbstractJingleConnection connection) {
        this.connections.remove(connection.getId());
    }

    public void finishConnectionOrThrow(final AbstractJingleConnection connection) {
        final AbstractJingleConnection.Id id = connection.getId();
        if (this.connections.remove(id) == null) {
            throw new IllegalStateException(
                    String.format("Unable to finish connection with id=%s", id.toString()));
        }
    }

    public boolean fireJingleRtpConnectionStateUpdates() {
        boolean firedUpdates = false;
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                final JingleRtpConnection jingleRtpConnection = (JingleRtpConnection) connection;
                if (jingleRtpConnection.isTerminated()) {
                    continue;
                }
                jingleRtpConnection.fireStateUpdate();
                firedUpdates = true;
            }
        }
        return firedUpdates;
    }

    public void retractSessionProposal(final Jid with) {
        synchronized (this.rtpSessionProposals) {
            RtpSessionProposal matchingProposal = null;
            for (RtpSessionProposal proposal : this.rtpSessionProposals.keySet()) {
                if (with.asBareJid().equals(proposal.with)) {
                    matchingProposal = proposal;
                    break;
                }
            }
            if (matchingProposal != null) {
                retractSessionProposal(matchingProposal);
            }
        }
    }

    private void retractSessionProposal(RtpSessionProposal rtpSessionProposal) {
        ToneManager.getInstance(context)
                .transition(RtpEndUserState.ENDED, rtpSessionProposal.media);
        LOGGER.debug(
                connection.getAccount().address
                        + ": retracting rtp session proposal with "
                        + rtpSessionProposal.with);
        this.rtpSessionProposals.remove(rtpSessionProposal);
        final Message messagePacket = MessageGenerator.sessionRetract(rtpSessionProposal);
        writeLogMissedOutgoing(rtpSessionProposal.with, rtpSessionProposal.sessionId, null);
        connection.sendMessagePacket(messagePacket);
    }

    public String initializeRtpSession(final Jid with, final Set<Media> media) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(with);
        final JingleRtpConnection rtpConnection =
                new JingleRtpConnection(
                        context, this.connection, id, this.connection.getBoundAddress());
        rtpConnection.setProposedMedia(media);
        this.connections.put(id, rtpConnection);
        rtpConnection.sendSessionInitiate();
        return id.sessionId;
    }

    public void proposeJingleRtpSession(final Jid with, final Set<Media> media) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                RtpSessionProposal proposal = entry.getKey();
                if (with.asBareJid().equals(proposal.with)) {
                    final DeviceDiscoveryState preexistingState = entry.getValue();
                    if (preexistingState != null
                            && preexistingState != DeviceDiscoveryState.FAILED) {
                        final RtpEndUserState endUserState = preexistingState.toEndUserState();
                        ToneManager.getInstance(context).transition(endUserState, media);
                        this.notifyJingleRtpConnectionUpdate(
                                with, proposal.sessionId, endUserState);
                        return;
                    }
                }
            }
            if (isBusy()) {
                if (hasMatchingRtpSession(with, media)) {
                    LOGGER.debug(
                            "ignoring request to propose jingle session because the other party"
                                    + " already created one for us");
                    return;
                }
                throw new IllegalStateException(
                        "There is already a running RTP session. This should have been caught by"
                                + " the UI");
            }
            final RtpSessionProposal proposal = RtpSessionProposal.of(with.asBareJid(), media);
            this.rtpSessionProposals.put(proposal, DeviceDiscoveryState.SEARCHING);
            this.notifyJingleRtpConnectionUpdate(
                    proposal.with, proposal.sessionId, RtpEndUserState.FINDING_DEVICE);
            final Message messagePacket = MessageGenerator.sessionProposal(proposal);
            connection.sendMessagePacket(messagePacket);
        }
    }

    public boolean hasMatchingProposal(final Jid with) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (with.asBareJid().equals(proposal.with)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void notifyRebound() {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            connection.notifyRebound();
        }
        // TODO the old version did this only when SM was enabled?!
        resendSessionProposals();
    }

    public WeakReference<JingleRtpConnection> findJingleRtpConnection(Jid with, String sessionId) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(with, sessionId);
        final AbstractJingleConnection connection = connections.get(id);
        if (connection instanceof JingleRtpConnection) {
            return new WeakReference<>((JingleRtpConnection) connection);
        }
        return null;
    }

    private void resendSessionProposals() {
        synchronized (this.rtpSessionProposals) {
            for (final Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (entry.getValue() == DeviceDiscoveryState.SEARCHING) {
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": resending session proposal to "
                                    + proposal.with);
                    final Message messagePacket = MessageGenerator.sessionProposal(proposal);
                    connection.sendMessagePacket(messagePacket);
                }
            }
        }
    }

    public void updateProposedSessionDiscovered(
            Jid from, String sessionId, final DeviceDiscoveryState target) {
        synchronized (this.rtpSessionProposals) {
            final RtpSessionProposal sessionProposal =
                    getRtpSessionProposal(from.asBareJid(), sessionId);
            final DeviceDiscoveryState currentState =
                    sessionProposal == null ? null : rtpSessionProposals.get(sessionProposal);
            if (currentState == null) {
                Log.d(Config.LOGTAG, "unable to find session proposal for session id " + sessionId);
                return;
            }
            if (currentState == DeviceDiscoveryState.DISCOVERED) {
                LOGGER.debug("session proposal already at discovered. not going to fall back");
                return;
            }
            this.rtpSessionProposals.put(sessionProposal, target);
            final RtpEndUserState endUserState = target.toEndUserState();
            ToneManager.getInstance(context).transition(endUserState, sessionProposal.media);
            this.notifyJingleRtpConnectionUpdate(
                    sessionProposal.with, sessionProposal.sessionId, endUserState);
            LOGGER.debug(
                    connection.getAccount().address
                            + ": flagging session "
                            + sessionId
                            + " as "
                            + target);
        }
    }

    public void rejectRtpSession(final String sessionId) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection.getId().sessionId.equals(sessionId)) {
                if (connection instanceof JingleRtpConnection) {
                    try {
                        ((JingleRtpConnection) connection).rejectCall();
                        return;
                    } catch (final IllegalStateException e) {
                        Log.w(
                                Config.LOGTAG,
                                "race condition on rejecting call from notification",
                                e);
                    }
                }
            }
        }
    }

    public void endRtpSession(final String sessionId) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection.getId().sessionId.equals(sessionId)) {
                if (connection instanceof JingleRtpConnection) {
                    ((JingleRtpConnection) connection).endCall();
                }
            }
        }
    }

    public void failProceed(final Jid with, final String sessionId, final String message) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(with, sessionId);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection instanceof JingleRtpConnection) {
            ((JingleRtpConnection) existingJingleConnection).deliverFailedProceed(message);
        }
    }

    public void ensureConnectionIsRegistered(final AbstractJingleConnection connection) {
        if (connections.containsValue(connection)) {
            return;
        }
        final IllegalStateException e =
                new IllegalStateException(
                        "JingleConnection has not been registered with connection manager");
        Log.e(Config.LOGTAG, "ensureConnectionIsRegistered() failed. Going to throw", e);
        throw e;
    }

    public void setTerminalSessionState(
            AbstractJingleConnection.Id id, final RtpEndUserState state, final Set<Media> media) {
        this.terminatedSessions.put(
                PersistableSessionId.of(id), new TerminatedRtpSession(state, media));
    }

    public TerminatedRtpSession getTerminalSessionState(final Jid with, final String sessionId) {
        return this.terminatedSessions.getIfPresent(new PersistableSessionId(with, sessionId));
    }

    public void notifyJingleRtpConnectionUpdate(
            final Jid with, final String sessionId, final RtpEndUserState state) {
        final var listener = this.onJingleRtpConnectionUpdate;
        if (listener == null) {
            return;
        }
        listener.onJingleRtpConnectionUpdate(with, sessionId, state);
    }

    public void notifyJingleRtpConnectionUpdate(
            AppRTCAudioManager.AudioDevice selectedAudioDevice,
            Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
        final var listener = this.onJingleRtpConnectionUpdate;
        if (listener == null) {
            return;
        }
        listener.onAudioDeviceChanged(selectedAudioDevice, availableAudioDevices);
    }

    public void setOnJingleRtpConnectionUpdate(final OnJingleRtpConnectionUpdate listener) {
        this.onJingleRtpConnectionUpdate = listener;
    }

    public RtpSessionNotification getNotificationService() {
        return this.rtpSessionNotification;
    }

    public interface OnJingleRtpConnectionUpdate {
        void onJingleRtpConnectionUpdate(
                final Jid with, final String sessionId, final RtpEndUserState state);

        void onAudioDeviceChanged(
                AppRTCAudioManager.AudioDevice selectedAudioDevice,
                Set<AppRTCAudioManager.AudioDevice> availableAudioDevices);
    }

    private static class PersistableSessionId {
        private final Jid with;
        private final String sessionId;

        private PersistableSessionId(Jid with, String sessionId) {
            this.with = with;
            this.sessionId = sessionId;
        }

        public static PersistableSessionId of(AbstractJingleConnection.Id id) {
            return new PersistableSessionId(id.with, id.sessionId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PersistableSessionId that = (PersistableSessionId) o;
            return Objects.equal(with, that.with) && Objects.equal(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(with, sessionId);
        }
    }

    public static class TerminatedRtpSession {
        public final RtpEndUserState state;
        public final Set<Media> media;

        TerminatedRtpSession(RtpEndUserState state, Set<Media> media) {
            this.state = state;
            this.media = media;
        }
    }

    public enum DeviceDiscoveryState {
        SEARCHING,
        SEARCHING_ACKNOWLEDGED,
        DISCOVERED,
        FAILED;

        public RtpEndUserState toEndUserState() {
            switch (this) {
                case SEARCHING:
                case SEARCHING_ACKNOWLEDGED:
                    return RtpEndUserState.FINDING_DEVICE;
                case DISCOVERED:
                    return RtpEndUserState.RINGING;
                default:
                    return RtpEndUserState.CONNECTIVITY_ERROR;
            }
        }
    }

    public static class RtpSessionProposal implements OngoingRtpSession {
        public final Jid with;
        public final String sessionId;
        public final Set<Media> media;

        private RtpSessionProposal(Jid with, String sessionId) {
            this(with, sessionId, Collections.emptySet());
        }

        private RtpSessionProposal(Jid with, String sessionId, Set<Media> media) {
            this.with = with;
            this.sessionId = sessionId;
            this.media = media;
        }

        public static RtpSessionProposal of(Jid with, Set<Media> media) {
            return new RtpSessionProposal(with, IDs.medium(), media);
        }

        @Override
        public Jid getWith() {
            return with;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RtpSessionProposal that = (RtpSessionProposal) o;
            return Objects.equal(with, that.with)
                    && Objects.equal(sessionId, that.sessionId)
                    && Objects.equal(media, that.media);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(with, sessionId, media);
        }
    }
}
