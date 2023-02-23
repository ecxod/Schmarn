package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.AppRTCAudioManager;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import im.conversations.android.BuildConfig;
import im.conversations.android.axolotl.AxolotlEncryptionException;
import im.conversations.android.axolotl.AxolotlService;
import im.conversations.android.dns.IP;
import im.conversations.android.notification.RtpSessionNotification;
import im.conversations.android.transformer.CallLogTransformation;
import im.conversations.android.util.BooleanFutures;
import im.conversations.android.xml.Element;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.AxolotlManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.ExternalDiscoManager;
import im.conversations.android.xmpp.manager.JingleConnectionManager;
import im.conversations.android.xmpp.model.disco.external.Service;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.jmi.Accept;
import im.conversations.android.xmpp.model.jmi.JingleMessage;
import im.conversations.android.xmpp.model.jmi.Proceed;
import im.conversations.android.xmpp.model.jmi.Propose;
import im.conversations.android.xmpp.model.jmi.Reject;
import im.conversations.android.xmpp.model.jmi.Retract;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;
import org.whispersystems.libsignal.IdentityKey;

public class JingleRtpConnection extends AbstractJingleConnection
        implements WebRTCWrapper.EventCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(JingleRtpConnection.class);
    public static final List<State> STATES_SHOWING_ONGOING_CALL =
            Arrays.asList(
                    State.PROCEED, State.SESSION_INITIALIZED_PRE_APPROVED, State.SESSION_ACCEPTED);
    private static final long BUSY_TIME_OUT = 30;
    private static final List<State> TERMINATED =
            Arrays.asList(
                    State.ACCEPTED,
                    State.REJECTED,
                    State.REJECTED_RACED,
                    State.RETRACTED,
                    State.RETRACTED_RACED,
                    State.TERMINATED_SUCCESS,
                    State.TERMINATED_DECLINED_OR_BUSY,
                    State.TERMINATED_CONNECTIVITY_ERROR,
                    State.TERMINATED_CANCEL_OR_TIMEOUT,
                    State.TERMINATED_APPLICATION_FAILURE,
                    State.TERMINATED_SECURITY_ERROR);

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder =
                new ImmutableMap.Builder<>();
        transitionBuilder.put(
                State.NULL,
                ImmutableList.of(
                        State.PROPOSED,
                        State.SESSION_INITIALIZED,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.PROPOSED,
                ImmutableList.of(
                        State.ACCEPTED,
                        State.PROCEED,
                        State.REJECTED,
                        State.RETRACTED,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR,
                        State.TERMINATED_CONNECTIVITY_ERROR // only used when the xmpp connection
                        // rebinds
                        ));
        transitionBuilder.put(
                State.PROCEED,
                ImmutableList.of(
                        State.REJECTED_RACED,
                        State.RETRACTED_RACED,
                        State.SESSION_INITIALIZED_PRE_APPROVED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR,
                        State.TERMINATED_CONNECTIVITY_ERROR // at this state used for error
                        // bounces of the proceed message
                        ));
        transitionBuilder.put(
                State.SESSION_INITIALIZED,
                ImmutableList.of(
                        State.SESSION_ACCEPTED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR, // at this state used for IQ errors
                        // and IQ timeouts
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.SESSION_INITIALIZED_PRE_APPROVED,
                ImmutableList.of(
                        State.SESSION_ACCEPTED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR, // at this state used for IQ errors
                        // and IQ timeouts
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.SESSION_ACCEPTED,
                ImmutableList.of(
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR,
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private final WebRTCWrapper webRTCWrapper = new WebRTCWrapper(this);
    private final Queue<Map.Entry<String, RtpContentMap.DescriptionTransport>>
            pendingIceCandidates = new LinkedList<>();
    private final OmemoVerification omemoVerification = new OmemoVerification();
    private State state = State.NULL;
    private Set<Media> proposedMedia;
    private RtpContentMap initiatorRtpContentMap;
    private RtpContentMap responderRtpContentMap;
    private RtpContentMap incomingContentAdd;
    private RtpContentMap outgoingContentAdd;
    private IceUdpTransportInfo.Setup peerDtlsSetup;
    private final Stopwatch sessionDuration = Stopwatch.createUnstarted();
    private final Queue<PeerConnection.PeerConnectionState> stateHistory = new LinkedList<>();
    private final RtpSessionNotification rtpSessionNotification;
    private ScheduledFuture<?> ringingTimeoutFuture;
    private final CallLogTransformation.Builder callLogTransformationBuilder;
    private final ListenableFuture<Boolean> remoteHasVideoFeature;

    public JingleRtpConnection(
            final Context context,
            final XmppConnection connection,
            final Id id,
            final Jid initiator) {
        super(context, connection, id, initiator);
        this.rtpSessionNotification =
                getManager(JingleConnectionManager.class).getNotificationService();
        this.remoteHasVideoFeature =
                getManager(DiscoManager.class)
                        .hasFeatureAsync(Entity.presence(id.with), Namespace.JINGLE_FEATURE_VIDEO);
        final Jid to = isInitiator() ? id.with : connection.getBoundAddress();
        this.callLogTransformationBuilder =
                new CallLogTransformation.Builder(id.with, to, initiator, id.sessionId);
    }

    private static State reasonToState(Reason reason) {
        switch (reason) {
            case SUCCESS:
                return State.TERMINATED_SUCCESS;
            case DECLINE:
            case BUSY:
                return State.TERMINATED_DECLINED_OR_BUSY;
            case CANCEL:
            case TIMEOUT:
                return State.TERMINATED_CANCEL_OR_TIMEOUT;
            case SECURITY_ERROR:
                return State.TERMINATED_SECURITY_ERROR;
            case FAILED_APPLICATION:
            case UNSUPPORTED_TRANSPORTS:
            case UNSUPPORTED_APPLICATIONS:
                return State.TERMINATED_APPLICATION_FAILURE;
            default:
                return State.TERMINATED_CONNECTIVITY_ERROR;
        }
    }

    @Override
    public synchronized void deliverPacket(final Iq iq) {
        final var jinglePacket = JinglePacket.upgrade(iq);
        switch (jinglePacket.getAction()) {
            case SESSION_INITIATE:
                receiveSessionInitiate(jinglePacket);
                break;
            case TRANSPORT_INFO:
                receiveTransportInfo(jinglePacket);
                break;
            case SESSION_ACCEPT:
                receiveSessionAccept(jinglePacket);
                break;
            case SESSION_TERMINATE:
                receiveSessionTerminate(jinglePacket);
                break;
            case CONTENT_ADD:
                receiveContentAdd(jinglePacket);
                break;
            case CONTENT_ACCEPT:
                receiveContentAccept(jinglePacket);
                break;
            case CONTENT_REJECT:
                receiveContentReject(jinglePacket);
                break;
            case CONTENT_REMOVE:
                receiveContentRemove(jinglePacket);
                break;
            default:
                respondOk(jinglePacket);
                LOGGER.debug(
                        String.format(
                                "%s: received unhandled jingle action %s",
                                connection.getAccount().address, jinglePacket.getAction()));
                break;
        }
    }

    @Override
    public synchronized void notifyRebound() {
        if (isTerminated()) {
            return;
        }
        webRTCWrapper.close();
        if (!isInitiator() && isInState(State.PROPOSED, State.SESSION_INITIALIZED)) {
            this.rtpSessionNotification.cancelIncomingCallNotification();
        }
        if (isInState(
                State.SESSION_INITIALIZED,
                State.SESSION_INITIALIZED_PRE_APPROVED,
                State.SESSION_ACCEPTED)) {
            // we might have already changed resources (full jid) at this point; so this might not
            // even reach the other party
            sendSessionTerminate(Reason.CONNECTIVITY_ERROR);
        } else {
            transitionOrThrow(State.TERMINATED_CONNECTIVITY_ERROR);
            finish();
        }
    }

    private void receiveSessionTerminate(final JinglePacket jinglePacket) {
        respondOk(jinglePacket);
        final JinglePacket.ReasonWrapper wrapper = jinglePacket.getReason();
        final State previous = this.state;
        LOGGER.debug(
                connection.getAccount().address
                        + ": received session terminate reason="
                        + wrapper.reason
                        + "("
                        + Strings.nullToEmpty(wrapper.text)
                        + ") while in state "
                        + previous);
        if (TERMINATED.contains(previous)) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": ignoring session terminate because already in "
                            + previous);
            return;
        }
        webRTCWrapper.close();
        final State target = reasonToState(wrapper.reason);
        transitionOrThrow(target);
        writeLogMessage(target);
        if (previous == State.PROPOSED || previous == State.SESSION_INITIALIZED) {
            this.rtpSessionNotification.cancelIncomingCallNotification();
        }
        finish();
    }

    private void receiveTransportInfo(final JinglePacket jinglePacket) {
        // Due to the asynchronicity of processing session-init we might move from NULL|PROCEED to
        // INITIALIZED only after transport-info has been received
        if (isInState(
                State.NULL,
                State.PROCEED,
                State.SESSION_INITIALIZED,
                State.SESSION_INITIALIZED_PRE_APPROVED,
                State.SESSION_ACCEPTED)) {
            final RtpContentMap contentMap;
            try {
                contentMap = RtpContentMap.of(jinglePacket);
            } catch (final IllegalArgumentException | NullPointerException e) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": improperly formatted contents; ignoring",
                        e);
                respondOk(jinglePacket);
                return;
            }
            receiveTransportInfo(jinglePacket, contentMap);
        } else {
            if (isTerminated()) {
                respondOk(jinglePacket);
                LOGGER.debug(
                        connection.getAccount().address
                                + ": ignoring out-of-order transport info; we where already"
                                + " terminated");
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": received transport info while in state="
                                + this.state);
                terminateWithOutOfOrder(jinglePacket);
            }
        }
    }

    private void receiveTransportInfo(
            final JinglePacket jinglePacket, final RtpContentMap contentMap) {
        final Set<Map.Entry<String, RtpContentMap.DescriptionTransport>> candidates =
                contentMap.contents.entrySet();
        if (this.state == State.SESSION_ACCEPTED) {
            // zero candidates + modified credentials are an ICE restart offer
            if (checkForIceRestart(jinglePacket, contentMap)) {
                return;
            }
            respondOk(jinglePacket);
            try {
                processCandidates(candidates);
            } catch (final WebRTCWrapper.PeerConnectionNotInitialized e) {
                LOGGER.warn(
                        connection.getAccount().address
                                + ": PeerConnection was not initialized when processing transport"
                                + " info. this usually indicates a race condition that can be"
                                + " ignored");
            }
        } else {
            respondOk(jinglePacket);
            pendingIceCandidates.addAll(candidates);
        }
    }

    private void receiveContentAdd(final JinglePacket jinglePacket) {
        final RtpContentMap modification;
        try {
            modification = RtpContentMap.of(jinglePacket);
            modification.requireContentDescriptions();
        } catch (final RuntimeException e) {
            LOGGER.debug(
                    connection.getAccount().address + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        if (isInState(State.SESSION_ACCEPTED)) {
            receiveContentAdd(jinglePacket, modification);
        } else {
            terminateWithOutOfOrder(jinglePacket);
        }
    }

    private void receiveContentAdd(
            final JinglePacket jinglePacket, final RtpContentMap modification) {
        final RtpContentMap remote = getRemoteContentMap();
        if (!Collections.disjoint(modification.getNames(), remote.getNames())) {
            respondOk(jinglePacket);
            this.webRTCWrapper.close();
            sendSessionTerminate(
                    Reason.FAILED_APPLICATION,
                    String.format(
                            "contents with names %s already exists",
                            Joiner.on(", ").join(modification.getNames())));
            return;
        }
        final ContentAddition contentAddition =
                ContentAddition.of(ContentAddition.Direction.INCOMING, modification);

        final RtpContentMap outgoing = this.outgoingContentAdd;
        final Set<ContentAddition.Summary> outgoingContentAddSummary =
                outgoing == null ? Collections.emptySet() : ContentAddition.summary(outgoing);

        if (outgoingContentAddSummary.equals(contentAddition.summary)) {
            if (isInitiator()) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": respond with tie break to matching content-add offer");
                respondWithTieBreak(jinglePacket);
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": automatically accept matching content-add offer");
                acceptContentAdd(contentAddition.summary, modification);
            }
            return;
        }

        // once we can display multiple video tracks we can be more loose with this condition
        // theoretically it should also be fine to automatically accept audio only contents
        if (Media.audioOnly(remote.getMedia()) && Media.videoOnly(contentAddition.media())) {
            LOGGER.debug(connection.getAccount().address + ": received " + contentAddition);
            this.incomingContentAdd = modification;
            respondOk(jinglePacket);
            updateEndUserState();
        } else {
            respondOk(jinglePacket);
            // TODO do we want to add a reason?
            rejectContentAdd(modification);
        }
    }

    private void receiveContentAccept(final JinglePacket jinglePacket) {
        final RtpContentMap receivedContentAccept;
        try {
            receivedContentAccept = RtpContentMap.of(jinglePacket);
            receivedContentAccept.requireContentDescriptions();
        } catch (final RuntimeException e) {
            LOGGER.debug(
                    connection.getAccount().address + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }

        final RtpContentMap outgoingContentAdd = this.outgoingContentAdd;
        if (outgoingContentAdd == null) {
            LOGGER.debug("received content-accept when we had no outgoing content add");
            terminateWithOutOfOrder(jinglePacket);
            return;
        }
        final Set<ContentAddition.Summary> ourSummary = ContentAddition.summary(outgoingContentAdd);
        if (ourSummary.equals(ContentAddition.summary(receivedContentAccept))) {
            this.outgoingContentAdd = null;
            respondOk(jinglePacket);
            receiveContentAccept(receivedContentAccept);
        } else {
            LOGGER.debug("received content-accept did not match our outgoing content-add");
            terminateWithOutOfOrder(jinglePacket);
        }
    }

    private void receiveContentAccept(final RtpContentMap receivedContentAccept) {
        final IceUdpTransportInfo.Setup peerDtlsSetup = getPeerDtlsSetup();
        final RtpContentMap modifiedContentMap =
                getRemoteContentMap().addContent(receivedContentAccept, peerDtlsSetup);

        setRemoteContentMap(modifiedContentMap);

        final SessionDescription answer = SessionDescription.of(modifiedContentMap, !isInitiator());

        final org.webrtc.SessionDescription sdp =
                new org.webrtc.SessionDescription(
                        org.webrtc.SessionDescription.Type.ANSWER, answer.toString());

        try {
            this.webRTCWrapper.setRemoteDescription(sdp).get();
        } catch (final Exception e) {
            final Throwable cause = Throwables.getRootCause(e);
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable to set remote description after receiving content-accept",
                    cause);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, cause.getMessage());
            return;
        }
        updateEndUserState();
        LOGGER.debug(
                connection.getAccount().address
                        + ": remote has accepted content-add "
                        + ContentAddition.summary(receivedContentAccept));
    }

    private void receiveContentReject(final JinglePacket jinglePacket) {
        final RtpContentMap receivedContentReject;
        try {
            receivedContentReject = RtpContentMap.of(jinglePacket);
        } catch (final RuntimeException e) {
            LOGGER.debug(
                    connection.getAccount().address + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            this.webRTCWrapper.close();
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }

        final RtpContentMap outgoingContentAdd = this.outgoingContentAdd;
        if (outgoingContentAdd == null) {
            LOGGER.debug("received content-reject when we had no outgoing content add");
            terminateWithOutOfOrder(jinglePacket);
            return;
        }
        final Set<ContentAddition.Summary> ourSummary = ContentAddition.summary(outgoingContentAdd);
        if (ourSummary.equals(ContentAddition.summary(receivedContentReject))) {
            this.outgoingContentAdd = null;
            respondOk(jinglePacket);
            LOGGER.debug(jinglePacket.toString());
            receiveContentReject(ourSummary);
        } else {
            LOGGER.debug("received content-reject did not match our outgoing content-add");
            terminateWithOutOfOrder(jinglePacket);
        }
    }

    private void receiveContentReject(final Set<ContentAddition.Summary> summary) {
        try {
            this.webRTCWrapper.removeTrack(Media.VIDEO);
            final RtpContentMap localContentMap = customRollback();
            modifyLocalContentMap(localContentMap);
        } catch (final Exception e) {
            final Throwable cause = Throwables.getRootCause(e);
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable to rollback local description after receiving"
                            + " content-reject",
                    cause);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, cause.getMessage());
            return;
        }
        LOGGER.debug(
                connection.getAccount().address
                        + ": remote has rejected our content-add "
                        + summary);
    }

    private void receiveContentRemove(final JinglePacket jinglePacket) {
        final RtpContentMap receivedContentRemove;
        try {
            receivedContentRemove = RtpContentMap.of(jinglePacket);
            receivedContentRemove.requireContentDescriptions();
        } catch (final RuntimeException e) {
            LOGGER.debug(
                    connection.getAccount().address + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            this.webRTCWrapper.close();
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        respondOk(jinglePacket);
        receiveContentRemove(receivedContentRemove);
    }

    private void receiveContentRemove(final RtpContentMap receivedContentRemove) {
        final RtpContentMap incomingContentAdd = this.incomingContentAdd;
        final Set<ContentAddition.Summary> contentAddSummary =
                incomingContentAdd == null
                        ? Collections.emptySet()
                        : ContentAddition.summary(incomingContentAdd);
        final Set<ContentAddition.Summary> removeSummary =
                ContentAddition.summary(receivedContentRemove);
        if (contentAddSummary.equals(removeSummary)) {
            this.incomingContentAdd = null;
            updateEndUserState();
        } else {
            webRTCWrapper.close();
            sendSessionTerminate(
                    Reason.FAILED_APPLICATION,
                    String.format(
                            "%s only supports %s as a means to retract a not yet accepted %s",
                            BuildConfig.APP_NAME,
                            JinglePacket.Action.CONTENT_REMOVE,
                            JinglePacket.Action.CONTENT_ACCEPT));
        }
    }

    public synchronized void retractContentAdd() {
        final RtpContentMap outgoingContentAdd = this.outgoingContentAdd;
        if (outgoingContentAdd == null) {
            throw new IllegalStateException("Not outgoing content add");
        }
        try {
            webRTCWrapper.removeTrack(Media.VIDEO);
            final RtpContentMap localContentMap = customRollback();
            modifyLocalContentMap(localContentMap);
        } catch (final Exception e) {
            final Throwable cause = Throwables.getRootCause(e);
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable to rollback local description after trying to retract"
                            + " content-add",
                    cause);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, cause.getMessage());
            return;
        }
        this.outgoingContentAdd = null;
        final JinglePacket retract =
                outgoingContentAdd
                        .toStub()
                        .toJinglePacket(JinglePacket.Action.CONTENT_REMOVE, id.sessionId);
        this.send(retract);
        LOGGER.debug(
                connection.getAccount().address
                        + ": retract content-add "
                        + ContentAddition.summary(outgoingContentAdd));
    }

    private RtpContentMap customRollback() throws ExecutionException, InterruptedException {
        final SessionDescription sdp = setLocalSessionDescription();
        final RtpContentMap localRtpContentMap = RtpContentMap.of(sdp, isInitiator());
        final SessionDescription answer = generateFakeResponse(localRtpContentMap);
        this.webRTCWrapper
                .setRemoteDescription(
                        new org.webrtc.SessionDescription(
                                org.webrtc.SessionDescription.Type.ANSWER, answer.toString()))
                .get();
        return localRtpContentMap;
    }

    private SessionDescription generateFakeResponse(final RtpContentMap localContentMap) {
        final RtpContentMap currentRemote = getRemoteContentMap();
        final RtpContentMap.Diff diff = currentRemote.diff(localContentMap);
        if (diff.isEmpty()) {
            throw new IllegalStateException(
                    "Unexpected rollback condition. No difference between local and remote");
        }
        final RtpContentMap patch = localContentMap.toContentModification(diff.added);
        if (ImmutableSet.of(Content.Senders.NONE).equals(patch.getSenders())) {
            final RtpContentMap nextRemote =
                    currentRemote.addContent(
                            patch.modifiedSenders(Content.Senders.NONE), getPeerDtlsSetup());
            return SessionDescription.of(nextRemote, !isInitiator());
        }
        throw new IllegalStateException(
                "Unexpected rollback condition. Senders were not uniformly none");
    }

    public synchronized void acceptContentAdd(
            @NonNull final Set<ContentAddition.Summary> contentAddition) {
        final RtpContentMap incomingContentAdd = this.incomingContentAdd;
        if (incomingContentAdd == null) {
            throw new IllegalStateException("No incoming content add");
        }

        if (contentAddition.equals(ContentAddition.summary(incomingContentAdd))) {
            this.incomingContentAdd = null;
            acceptContentAdd(contentAddition, incomingContentAdd);
        } else {
            throw new IllegalStateException(
                    "Accepted content add does not match pending content-add");
        }
    }

    private void acceptContentAdd(
            @NonNull final Set<ContentAddition.Summary> contentAddition,
            final RtpContentMap incomingContentAdd) {
        final IceUdpTransportInfo.Setup setup = getPeerDtlsSetup();
        final RtpContentMap modifiedContentMap =
                getRemoteContentMap().addContent(incomingContentAdd, setup);
        this.setRemoteContentMap(modifiedContentMap);

        final SessionDescription offer;
        try {
            offer = SessionDescription.of(modifiedContentMap, !isInitiator());
        } catch (final IllegalArgumentException | NullPointerException e) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable convert offer from content-add to SDP",
                    e);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, e.getMessage());
            return;
        }
        this.incomingContentAdd = null;
        acceptContentAdd(contentAddition, offer);
    }

    private void acceptContentAdd(
            final Set<ContentAddition.Summary> contentAddition, final SessionDescription offer) {
        final org.webrtc.SessionDescription sdp =
                new org.webrtc.SessionDescription(
                        org.webrtc.SessionDescription.Type.OFFER, offer.toString());
        try {
            this.webRTCWrapper.setRemoteDescription(sdp).get();

            // TODO add tracks for 'media' where contentAddition.senders matches

            // TODO if senders.sending(isInitiator())

            this.webRTCWrapper.addTrack(Media.VIDEO);

            // TODO add additional transceivers for recv only cases

            final SessionDescription answer = setLocalSessionDescription();
            final RtpContentMap rtpContentMap = RtpContentMap.of(answer, isInitiator());

            final RtpContentMap contentAcceptMap =
                    rtpContentMap.toContentModification(
                            Collections2.transform(contentAddition, ca -> ca.name));
            LOGGER.debug(
                    connection.getAccount().address
                            + ": sending content-accept "
                            + ContentAddition.summary(contentAcceptMap));
            modifyLocalContentMap(rtpContentMap);
            sendContentAccept(contentAcceptMap);
        } catch (final Exception e) {
            LOGGER.debug("unable to accept content add", Throwables.getRootCause(e));
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION);
        }
    }

    private void sendContentAccept(final RtpContentMap contentAcceptMap) {
        final JinglePacket jinglePacket =
                contentAcceptMap.toJinglePacket(JinglePacket.Action.CONTENT_ACCEPT, id.sessionId);
        send(jinglePacket);
    }

    public synchronized void rejectContentAdd() {
        final RtpContentMap incomingContentAdd = this.incomingContentAdd;
        if (incomingContentAdd == null) {
            throw new IllegalStateException("No incoming content add");
        }
        this.incomingContentAdd = null;
        updateEndUserState();
        rejectContentAdd(incomingContentAdd);
    }

    private void rejectContentAdd(final RtpContentMap contentMap) {
        final JinglePacket jinglePacket =
                contentMap
                        .toStub()
                        .toJinglePacket(JinglePacket.Action.CONTENT_REJECT, id.sessionId);
        LOGGER.debug(
                connection.getAccount().address
                        + ": rejecting content "
                        + ContentAddition.summary(contentMap));
        send(jinglePacket);
    }

    private boolean checkForIceRestart(final Iq jinglePacket, final RtpContentMap rtpContentMap) {
        final RtpContentMap existing = getRemoteContentMap();
        final Set<IceUdpTransportInfo.Credentials> existingCredentials;
        final IceUdpTransportInfo.Credentials newCredentials;
        try {
            existingCredentials = existing.getCredentials();
            newCredentials = rtpContentMap.getDistinctCredentials();
        } catch (final IllegalStateException e) {
            LOGGER.debug("unable to gather credentials for comparison", e);
            return false;
        }
        if (existingCredentials.contains(newCredentials)) {
            return false;
        }
        // TODO an alternative approach is to check if we already got an iq result to our
        // ICE-restart
        // and if that's the case we are seeing an answer.
        // This might be more spec compliant but also more error prone potentially
        final boolean isOffer = rtpContentMap.emptyCandidates();
        final RtpContentMap restartContentMap;
        try {
            if (isOffer) {
                LOGGER.debug("received offer to restart ICE " + newCredentials);
                restartContentMap =
                        existing.modifiedCredentials(
                                newCredentials, IceUdpTransportInfo.Setup.ACTPASS);
            } else {
                final IceUdpTransportInfo.Setup setup = getPeerDtlsSetup();
                LOGGER.debug(
                        "received confirmation of ICE restart"
                                + newCredentials
                                + " peer_setup="
                                + setup);
                // DTLS setup attribute needs to be rewritten to reflect current peer state
                // https://groups.google.com/g/discuss-webrtc/c/DfpIMwvUfeM
                restartContentMap = existing.modifiedCredentials(newCredentials, setup);
            }
            if (applyIceRestart(jinglePacket, restartContentMap, isOffer)) {
                return isOffer;
            } else {
                LOGGER.debug("ignoring ICE restart. sending tie-break");
                respondWithTieBreak(jinglePacket);
                return true;
            }
        } catch (final Exception exception) {
            respondOk(jinglePacket);
            final Throwable rootCause = Throwables.getRootCause(exception);
            if (rootCause instanceof WebRTCWrapper.PeerConnectionNotInitialized) {
                // If this happens a termination is already in progress
                LOGGER.debug("ignoring PeerConnectionNotInitialized on ICE restart");
                return true;
            }
            LOGGER.debug("failure to apply ICE restart", rootCause);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.ofThrowable(rootCause), rootCause.getMessage());
            return true;
        }
    }

    private IceUdpTransportInfo.Setup getPeerDtlsSetup() {
        final IceUdpTransportInfo.Setup peerSetup = this.peerDtlsSetup;
        if (peerSetup == null || peerSetup == IceUdpTransportInfo.Setup.ACTPASS) {
            throw new IllegalStateException("Invalid peer setup");
        }
        return peerSetup;
    }

    private void storePeerDtlsSetup(final IceUdpTransportInfo.Setup setup) {
        if (setup == null || setup == IceUdpTransportInfo.Setup.ACTPASS) {
            throw new IllegalArgumentException("Trying to store invalid peer dtls setup");
        }
        this.peerDtlsSetup = setup;
    }

    private boolean applyIceRestart(
            final Iq jinglePacket, final RtpContentMap restartContentMap, final boolean isOffer)
            throws ExecutionException, InterruptedException {
        final SessionDescription sessionDescription =
                SessionDescription.of(restartContentMap, !isInitiator());
        final org.webrtc.SessionDescription.Type type =
                isOffer
                        ? org.webrtc.SessionDescription.Type.OFFER
                        : org.webrtc.SessionDescription.Type.ANSWER;
        org.webrtc.SessionDescription sdp =
                new org.webrtc.SessionDescription(type, sessionDescription.toString());
        if (isOffer && webRTCWrapper.getSignalingState() != PeerConnection.SignalingState.STABLE) {
            if (isInitiator()) {
                // We ignore the offer and respond with tie-break. This will clause the responder
                // not to apply the content map
                return false;
            }
        }
        webRTCWrapper.setRemoteDescription(sdp).get();
        setRemoteContentMap(restartContentMap);
        if (isOffer) {
            webRTCWrapper.setIsReadyToReceiveIceCandidates(false);
            final SessionDescription localSessionDescription = setLocalSessionDescription();
            setLocalContentMap(RtpContentMap.of(localSessionDescription, isInitiator()));
            // We need to respond OK before sending any candidates
            respondOk(jinglePacket);
            webRTCWrapper.setIsReadyToReceiveIceCandidates(true);
        } else {
            storePeerDtlsSetup(restartContentMap.getDtlsSetup());
        }
        return true;
    }

    private void processCandidates(
            final Set<Map.Entry<String, RtpContentMap.DescriptionTransport>> contents) {
        for (final Map.Entry<String, RtpContentMap.DescriptionTransport> content : contents) {
            processCandidate(content);
        }
    }

    private void processCandidate(
            final Map.Entry<String, RtpContentMap.DescriptionTransport> content) {
        final RtpContentMap rtpContentMap = getRemoteContentMap();
        final List<String> indices = toIdentificationTags(rtpContentMap);
        final String sdpMid = content.getKey(); // aka content name
        final IceUdpTransportInfo transport = content.getValue().transport;
        final IceUdpTransportInfo.Credentials credentials = transport.getCredentials();

        // TODO check that credentials remained the same

        for (final IceUdpTransportInfo.Candidate candidate : transport.getCandidates()) {
            final String sdp;
            try {
                sdp = candidate.toSdpAttribute(credentials.ufrag);
            } catch (final IllegalArgumentException e) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": ignoring invalid ICE candidate "
                                + e.getMessage());
                continue;
            }
            final int mLineIndex = indices.indexOf(sdpMid);
            if (mLineIndex < 0) {
                LOGGER.warn(
                        "mLineIndex not found for " + sdpMid + ". available indices " + indices);
            }
            final IceCandidate iceCandidate = new IceCandidate(sdpMid, mLineIndex, sdp);
            LOGGER.debug("received candidate: " + iceCandidate);
            this.webRTCWrapper.addIceCandidate(iceCandidate);
        }
    }

    private RtpContentMap getRemoteContentMap() {
        return isInitiator() ? this.responderRtpContentMap : this.initiatorRtpContentMap;
    }

    private RtpContentMap getLocalContentMap() {
        return isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
    }

    private List<String> toIdentificationTags(final RtpContentMap rtpContentMap) {
        final Group originalGroup = rtpContentMap.group;
        final List<String> identificationTags =
                originalGroup == null
                        ? rtpContentMap.getNames()
                        : originalGroup.getIdentificationTags();
        if (identificationTags.size() == 0) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": no identification tags found in initial offer. we won't be able"
                            + " to calculate mLineIndices");
        }
        return identificationTags;
    }

    private ListenableFuture<RtpContentMap> receiveRtpContentMap(
            final JinglePacket jinglePacket, final boolean expectVerification) {
        final RtpContentMap receivedContentMap;
        try {
            receivedContentMap = RtpContentMap.of(jinglePacket);
        } catch (final Exception e) {
            return Futures.immediateFailedFuture(e);
        }
        if (receivedContentMap instanceof OmemoVerifiedRtpContentMap) {
            final ListenableFuture<AxolotlService.OmemoVerifiedPayload<RtpContentMap>> future =
                    getManager(AxolotlManager.class)
                            .decrypt((OmemoVerifiedRtpContentMap) receivedContentMap, id.with);
            return Futures.transform(
                    future,
                    omemoVerifiedPayload -> {
                        // TODO test if an exception here triggers a correct abort
                        omemoVerification.setOrEnsureEqual(omemoVerifiedPayload);
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": received verifiable DTLS fingerprint via "
                                        + omemoVerification);
                        return omemoVerifiedPayload.getPayload();
                    },
                    MoreExecutors.directExecutor());
        } else if (Config.REQUIRE_RTP_VERIFICATION || expectVerification) {
            return Futures.immediateFailedFuture(
                    new SecurityException("DTLS fingerprint was unexpectedly not verifiable"));
        } else {
            return Futures.immediateFuture(receivedContentMap);
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            LOGGER.debug(
                    String.format(
                            "%s: received session-initiate even though we were initiating",
                            connection.getAccount().address));
            if (isTerminated()) {
                LOGGER.debug(
                        String.format(
                                "%s: got a reason to terminate with out-of-order. but already in"
                                        + " state %s",
                                connection.getAccount().address, getState()));
                respondWithOutOfOrder(jinglePacket);
            } else {
                terminateWithOutOfOrder(jinglePacket);
            }
            return;
        }
        final ListenableFuture<RtpContentMap> future = receiveRtpContentMap(jinglePacket, false);
        Futures.addCallback(
                future,
                new FutureCallback<RtpContentMap>() {
                    @Override
                    public void onSuccess(@Nullable RtpContentMap rtpContentMap) {
                        receiveSessionInitiate(jinglePacket, rtpContentMap);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        respondOk(jinglePacket);
                        sendSessionTerminate(Reason.ofThrowable(throwable), throwable.getMessage());
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void receiveSessionInitiate(
            final JinglePacket jinglePacket, final RtpContentMap contentMap) {
        try {
            contentMap.requireContentDescriptions();
            contentMap.requireDTLSFingerprint(true);
        } catch (final RuntimeException e) {
            LOGGER.debug(
                    connection.getAccount().address + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        LOGGER.debug("processing session-init with " + contentMap.contents.size() + " contents");
        final State target;
        if (this.state == State.PROCEED) {
            Preconditions.checkState(
                    proposedMedia != null && proposedMedia.size() > 0,
                    "proposed media must be set when processing pre-approved session-initiate");
            if (!this.proposedMedia.equals(contentMap.getMedia())) {
                sendSessionTerminate(
                        Reason.SECURITY_ERROR,
                        String.format(
                                "Your session proposal (Jingle Message Initiation) included media"
                                        + " %s but your session-initiate was %s",
                                this.proposedMedia, contentMap.getMedia()));
                return;
            }
            target = State.SESSION_INITIALIZED_PRE_APPROVED;
        } else {
            target = State.SESSION_INITIALIZED;
        }
        if (transition(target, () -> this.initiatorRtpContentMap = contentMap)) {
            respondOk(jinglePacket);
            pendingIceCandidates.addAll(contentMap.contents.entrySet());
            if (target == State.SESSION_INITIALIZED_PRE_APPROVED) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": automatically accepting session-initiate");
                sendSessionAccept();
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": received not pre-approved session-initiate. start ringing");
                startRinging();
            }
        } else {
            LOGGER.debug(
                    String.format(
                            "%s: received session-initiate while in state %s",
                            connection.getAccount().address, state));
            terminateWithOutOfOrder(jinglePacket);
        }
    }

    private void receiveSessionAccept(final JinglePacket jinglePacket) {
        if (!isInitiator()) {
            LOGGER.debug(
                    String.format(
                            "%s: received session-accept even though we were responding",
                            connection.getAccount().address));
            terminateWithOutOfOrder(jinglePacket);
            return;
        }
        final ListenableFuture<RtpContentMap> future =
                receiveRtpContentMap(jinglePacket, this.omemoVerification.hasFingerprint());
        Futures.addCallback(
                future,
                new FutureCallback<RtpContentMap>() {
                    @Override
                    public void onSuccess(@Nullable RtpContentMap rtpContentMap) {
                        receiveSessionAccept(jinglePacket, rtpContentMap);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        respondOk(jinglePacket);
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": improperly formatted contents in session-accept",
                                throwable);
                        webRTCWrapper.close();
                        sendSessionTerminate(Reason.ofThrowable(throwable), throwable.getMessage());
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void receiveSessionAccept(final Iq jinglePacket, final RtpContentMap contentMap) {
        try {
            contentMap.requireContentDescriptions();
            contentMap.requireDTLSFingerprint();
        } catch (final RuntimeException e) {
            respondOk(jinglePacket);
            LOGGER.debug(
                    connection.getAccount().address
                            + ": improperly formatted contents in session-accept",
                    e);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        final Set<Media> initiatorMedia = this.initiatorRtpContentMap.getMedia();
        if (!initiatorMedia.equals(contentMap.getMedia())) {
            sendSessionTerminate(
                    Reason.SECURITY_ERROR,
                    String.format(
                            "Your session-included included media %s but our session-initiate was"
                                    + " %s",
                            this.proposedMedia, contentMap.getMedia()));
            return;
        }
        LOGGER.debug("processing session-accept with " + contentMap.contents.size() + " contents");
        if (transition(State.SESSION_ACCEPTED)) {
            respondOk(jinglePacket);
            receiveSessionAccept(contentMap);
        } else {
            LOGGER.debug(
                    String.format(
                            "%s: received session-accept while in state %s",
                            connection.getAccount().address, state));
            respondOk(jinglePacket);
        }
    }

    private void receiveSessionAccept(final RtpContentMap contentMap) {
        this.responderRtpContentMap = contentMap;
        this.storePeerDtlsSetup(contentMap.getDtlsSetup());
        final SessionDescription sessionDescription;
        try {
            sessionDescription = SessionDescription.of(contentMap, false);
        } catch (final IllegalArgumentException | NullPointerException e) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable convert offer from session-accept to SDP",
                    e);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, e.getMessage());
            return;
        }
        final org.webrtc.SessionDescription answer =
                new org.webrtc.SessionDescription(
                        org.webrtc.SessionDescription.Type.ANSWER, sessionDescription.toString());
        try {
            this.webRTCWrapper.setRemoteDescription(answer).get();
        } catch (final Exception e) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable to set remote description after receiving session-accept",
                    Throwables.getRootCause(e));
            webRTCWrapper.close();
            sendSessionTerminate(
                    Reason.FAILED_APPLICATION, Throwables.getRootCause(e).getMessage());
            return;
        }
        processCandidates(contentMap.contents.entrySet());
    }

    private void sendSessionAccept() {
        final RtpContentMap rtpContentMap = this.initiatorRtpContentMap;
        if (rtpContentMap == null) {
            throw new IllegalStateException("initiator RTP Content Map has not been set");
        }
        final SessionDescription offer;
        try {
            offer = SessionDescription.of(rtpContentMap, true);
        } catch (final IllegalArgumentException | NullPointerException e) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable convert offer from session-initiate to SDP",
                    e);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, e.getMessage());
            return;
        }
        sendSessionAccept(rtpContentMap.getMedia(), offer);
    }

    private void sendSessionAccept(final Set<Media> media, final SessionDescription offer) {
        discoverIceServers(iceServers -> sendSessionAccept(media, offer, iceServers));
    }

    private synchronized void sendSessionAccept(
            final Set<Media> media,
            final SessionDescription offer,
            final List<PeerConnection.IceServer> iceServers) {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": ICE servers got discovered when session was already terminated."
                            + " nothing to do.");
            return;
        }
        try {
            setupWebRTC(media, iceServers);
        } catch (final WebRTCWrapper.InitializationException e) {
            LOGGER.debug(connection.getAccount().address + ": unable to initialize WebRTC");
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, e.getMessage());
            return;
        }
        final org.webrtc.SessionDescription sdp =
                new org.webrtc.SessionDescription(
                        org.webrtc.SessionDescription.Type.OFFER, offer.toString());
        try {
            this.webRTCWrapper.setRemoteDescription(sdp).get();
            addIceCandidatesFromBlackLog();
            org.webrtc.SessionDescription webRTCSessionDescription =
                    this.webRTCWrapper.setLocalDescription().get();
            prepareSessionAccept(webRTCSessionDescription);
        } catch (final Exception e) {
            failureToAcceptSession(e);
        }
    }

    private void failureToAcceptSession(final Throwable throwable) {
        if (isTerminated()) {
            return;
        }
        final Throwable rootCause = Throwables.getRootCause(throwable);
        LOGGER.debug("unable to send session accept", rootCause);
        webRTCWrapper.close();
        sendSessionTerminate(Reason.ofThrowable(rootCause), rootCause.getMessage());
    }

    private void addIceCandidatesFromBlackLog() {
        Map.Entry<String, RtpContentMap.DescriptionTransport> foo;
        while ((foo = this.pendingIceCandidates.poll()) != null) {
            processCandidate(foo);
            LOGGER.debug(connection.getAccount().address + ": added candidate from back log");
        }
    }

    private void prepareSessionAccept(
            final org.webrtc.SessionDescription webRTCSessionDescription) {
        final SessionDescription sessionDescription =
                SessionDescription.parse(webRTCSessionDescription.description);
        final RtpContentMap respondingRtpContentMap = RtpContentMap.of(sessionDescription, false);
        this.responderRtpContentMap = respondingRtpContentMap;
        storePeerDtlsSetup(respondingRtpContentMap.getDtlsSetup().flip());
        final ListenableFuture<RtpContentMap> outgoingContentMapFuture =
                prepareOutgoingContentMap(respondingRtpContentMap);
        Futures.addCallback(
                outgoingContentMapFuture,
                new FutureCallback<RtpContentMap>() {
                    @Override
                    public void onSuccess(final RtpContentMap outgoingContentMap) {
                        sendSessionAccept(outgoingContentMap);
                        webRTCWrapper.setIsReadyToReceiveIceCandidates(true);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        failureToAcceptSession(throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void sendSessionAccept(final RtpContentMap rtpContentMap) {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": preparing session accept was too slow. already terminated."
                            + " nothing to do.");
            return;
        }
        transitionOrThrow(State.SESSION_ACCEPTED);
        final JinglePacket sessionAccept =
                rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_ACCEPT, id.sessionId);
        send(sessionAccept);
    }

    private ListenableFuture<RtpContentMap> prepareOutgoingContentMap(
            final RtpContentMap rtpContentMap) {
        if (this.omemoVerification.hasDeviceId()) {
            ListenableFuture<AxolotlService.OmemoVerifiedPayload<OmemoVerifiedRtpContentMap>>
                    verifiedPayloadFuture =
                            getManager(AxolotlManager.class)
                                    .encrypt(
                                            rtpContentMap,
                                            id.with,
                                            omemoVerification.getDeviceId());
            return Futures.transform(
                    verifiedPayloadFuture,
                    verifiedPayload -> {
                        omemoVerification.setOrEnsureEqual(verifiedPayload);
                        return verifiedPayload.getPayload();
                    },
                    MoreExecutors.directExecutor());
        } else {
            return Futures.immediateFuture(rtpContentMap);
        }
    }

    public synchronized void deliveryMessage(
            final Jid from, final JingleMessage message, final String serverMessageId) {
        LOGGER.debug(
                connection.getAccount().address
                        + ": delivered message to JingleRtpConnection "
                        + message);
        if (message instanceof Propose) {
            receivePropose(from, (Propose) message, serverMessageId);
        } else if (message instanceof Proceed) {
            receiveProceed(from, (Proceed) message, serverMessageId);
        } else if (message instanceof Retract) {
            receiveRetract(from, serverMessageId);
        } else if (message instanceof Reject) {
            receiveReject(from, serverMessageId);
        } else if (message instanceof Accept) {
            receiveAccept(from, serverMessageId);
        }
    }

    public void deliverFailedProceed(final String message) {
        LOGGER.debug(
                connection.getAccount().address
                        + ": receive message error for proceed message ("
                        + Strings.nullToEmpty(message)
                        + ")");
        if (transition(State.TERMINATED_CONNECTIVITY_ERROR)) {
            webRTCWrapper.close();
            LOGGER.debug(
                    connection.getAccount().address + ": transitioned into connectivity error");
            this.finish();
        }
    }

    private void receiveAccept(final Jid from, final String serverMsgId) {
        final boolean originatedFromMyself =
                from.asBareJid().equals(connection.getAccount().address);
        if (originatedFromMyself) {
            if (transition(State.ACCEPTED)) {
                acceptedOnOtherDevice(serverMsgId);
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": unable to transition to accept because already in state="
                                + this.state);
            }
        } else {
            LOGGER.debug(connection.getAccount().address + ": ignoring 'accept' from " + from);
        }
    }

    private void acceptedOnOtherDevice(final String serverMsgId) {
        if (serverMsgId != null) {
            this.callLogTransformationBuilder.setStanzaId(serverMsgId);
        }
        this.callLogTransformationBuilder.setCarbon(
                true); // indicate that call was accepted on other device
        this.writeLogMessageSuccess(0);
        this.rtpSessionNotification.cancelIncomingCallNotification();
        this.finish();
    }

    private void receiveReject(final Jid from, final String serverMsgId) {
        final boolean originatedFromMyself =
                from.asBareJid().equals(connection.getAccount().address);
        // reject from another one of my clients
        if (originatedFromMyself) {
            receiveRejectFromMyself(serverMsgId);
        } else if (isInitiator()) {
            if (from.equals(id.with)) {
                receiveRejectFromResponder();
            } else {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": ignoring reject from "
                                + from
                                + " for session with "
                                + id.with);
            }
        } else {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": ignoring reject from "
                            + from
                            + " for session with "
                            + id.with);
        }
    }

    private void receiveRejectFromMyself(final String serverMsgId) {
        if (transition(State.REJECTED)) {
            this.rtpSessionNotification.cancelIncomingCallNotification();
            this.finish();
            if (serverMsgId != null) {
                this.callLogTransformationBuilder.setStanzaId(serverMsgId);
            }
            this.callLogTransformationBuilder.setCarbon(
                    true); // indicate that call was rejected on other device
            writeLogMessageMissed();
        } else {
            LOGGER.debug("not able to transition into REJECTED because already in " + this.state);
        }
    }

    private void receiveRejectFromResponder() {
        if (isInState(State.PROCEED)) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": received reject while still in proceed. callee reconsidered");
            closeTransitionLogFinish(State.REJECTED_RACED);
            return;
        }
        if (isInState(State.SESSION_INITIALIZED_PRE_APPROVED)) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": received reject while in SESSION_INITIATED_PRE_APPROVED. callee"
                            + " reconsidered before receiving session-init");
            closeTransitionLogFinish(State.TERMINATED_DECLINED_OR_BUSY);
            return;
        }
        LOGGER.debug(
                connection.getAccount().address
                        + ": ignoring reject from responder because already in state "
                        + this.state);
    }

    private void receivePropose(final Jid from, final Propose propose, final String serverMsgId) {
        final boolean originatedFromMyself =
                from.asBareJid().equals(connection.getAccount().address);
        if (originatedFromMyself) {
            LOGGER.debug(connection.getAccount().address + ": saw proposal from myself. ignoring");
        } else if (transition(
                State.PROPOSED,
                () -> {
                    final Collection<RtpDescription> descriptions =
                            Collections2.transform(
                                    Collections2.filter(
                                            propose.getDescriptions(),
                                            d -> d instanceof RtpDescription),
                                    input -> (RtpDescription) input);
                    final Collection<Media> media =
                            Collections2.transform(descriptions, RtpDescription::getMedia);
                    Preconditions.checkState(
                            !media.contains(Media.UNKNOWN),
                            "RTP descriptions contain unknown media");
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": received session proposal from "
                                    + from
                                    + " for "
                                    + media);
                    this.proposedMedia = Sets.newHashSet(media);
                })) {
            if (serverMsgId != null) {
                this.callLogTransformationBuilder.setStanzaId(serverMsgId);
            }
            startRinging();
        } else {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": ignoring session proposal because already in "
                            + state);
        }
    }

    private void startRinging() {
        LOGGER.debug(
                connection.getAccount().address
                        + ": received call from "
                        + id.with
                        + ". start ringing");
        ringingTimeoutFuture =
                getManager(JingleConnectionManager.class)
                        .schedule(this::ringingTimeout, BUSY_TIME_OUT, TimeUnit.SECONDS);
        rtpSessionNotification.startRinging(getAccount(), id, getMedia());
    }

    private synchronized void ringingTimeout() {
        LOGGER.debug(connection.getAccount().address + ": timeout reached for ringing");
        switch (this.state) {
            case PROPOSED:
                callLogTransformationBuilder.markUnread();
                rejectCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                callLogTransformationBuilder.markUnread();
                rejectCallFromSessionInitiate();
                break;
        }
        rtpSessionNotification.pushMissedCallNow(callLogTransformationBuilder.build());
    }

    private void cancelRingingTimeout() {
        final ScheduledFuture<?> future = this.ringingTimeoutFuture;
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private void receiveProceed(final Jid from, final Proceed proceed, final String serverMsgId) {
        final Set<Media> media =
                Preconditions.checkNotNull(
                        this.proposedMedia, "Proposed media has to be set before handling proceed");
        Preconditions.checkState(media.size() > 0, "Proposed media should not be empty");
        if (from.equals(id.with)) {
            if (isInitiator()) {
                if (transition(State.PROCEED)) {
                    if (serverMsgId != null) {
                        this.callLogTransformationBuilder.setStanzaId(serverMsgId);
                    }
                    final Integer remoteDeviceId = proceed.getDeviceId();
                    if (isOmemoEnabled()) {
                        this.omemoVerification.setDeviceId(remoteDeviceId);
                    } else {
                        if (remoteDeviceId != null) {
                            LOGGER.debug(
                                    connection.getAccount().address
                                            + ": remote party signaled support for OMEMO"
                                            + " verification but we have OMEMO disabled");
                        }
                        this.omemoVerification.setDeviceId(null);
                    }
                    this.sendSessionInitiate(media, State.SESSION_INITIALIZED_PRE_APPROVED);
                } else {
                    LOGGER.debug(
                            String.format(
                                    "%s: ignoring proceed because already in %s",
                                    connection.getAccount().address, this.state));
                }
            } else {
                LOGGER.debug(
                        String.format(
                                "%s: ignoring proceed because we were not initializing",
                                connection.getAccount().address));
            }
        } else if (from.asBareJid().equals(connection.getAccount().address)) {
            if (transition(State.ACCEPTED)) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": moved session with "
                                + id.with
                                + " into state accepted after received carbon copied proceed");
                acceptedOnOtherDevice(serverMsgId);
            }
        } else {
            LOGGER.debug(
                    String.format(
                            "%s: ignoring proceed from %s. was expected from %s",
                            connection.getAccount().address, from, id.with));
        }
    }

    private void receiveRetract(final Jid from, final String serverMsgId) {
        if (from.equals(id.with)) {
            final State target =
                    this.state == State.PROCEED ? State.RETRACTED_RACED : State.RETRACTED;
            if (transition(target)) {
                rtpSessionNotification.cancelIncomingCallNotification();
                rtpSessionNotification.pushMissedCallNow(callLogTransformationBuilder.build());
                LOGGER.debug(
                        connection.getAccount().address
                                + ": session with "
                                + id.with
                                + " has been retracted (serverMsgId="
                                + serverMsgId
                                + ")");
                if (serverMsgId != null) {
                    this.callLogTransformationBuilder.setStanzaId(serverMsgId);
                }
                if (target == State.RETRACTED) {
                    this.callLogTransformationBuilder.markUnread();
                }
                writeLogMessageMissed();
                finish();
            } else {
                LOGGER.debug("ignoring retract because already in " + this.state);
            }
        } else {
            // TODO parse retract from self
            LOGGER.debug(
                    connection.getAccount().address
                            + ": received retract from "
                            + from
                            + ". expected retract from"
                            + id.with
                            + ". ignoring");
        }
    }

    public void sendSessionInitiate() {
        sendSessionInitiate(this.proposedMedia, State.SESSION_INITIALIZED);
    }

    private void sendSessionInitiate(final Set<Media> media, final State targetState) {
        LOGGER.debug(connection.getAccount().address + ": prepare session-initiate");
        discoverIceServers(iceServers -> sendSessionInitiate(media, targetState, iceServers));
    }

    private synchronized void sendSessionInitiate(
            final Set<Media> media,
            final State targetState,
            final List<PeerConnection.IceServer> iceServers) {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": ICE servers got discovered when session was already terminated."
                            + " nothing to do.");
            return;
        }
        try {
            setupWebRTC(media, iceServers);
        } catch (final WebRTCWrapper.InitializationException e) {
            LOGGER.debug(connection.getAccount().address + ": unable to initialize WebRTC");
            webRTCWrapper.close();
            sendRetract(Reason.ofThrowable(e));
            return;
        }
        try {
            org.webrtc.SessionDescription webRTCSessionDescription =
                    this.webRTCWrapper.setLocalDescription().get();
            prepareSessionInitiate(webRTCSessionDescription, targetState);
        } catch (final Exception e) {
            // TODO sending the error text is worthwhile as well. Especially for FailureToSet
            // exceptions
            failureToInitiateSession(e, targetState);
        }
    }

    private void failureToInitiateSession(final Throwable throwable, final State targetState) {
        if (isTerminated()) {
            return;
        }
        LOGGER.debug(
                connection.getAccount().address + ": unable to sendSessionInitiate",
                Throwables.getRootCause(throwable));
        webRTCWrapper.close();
        final Reason reason = Reason.ofThrowable(throwable);
        if (isInState(targetState)) {
            sendSessionTerminate(reason, throwable.getMessage());
        } else {
            sendRetract(reason);
        }
    }

    private void sendRetract(final Reason reason) {
        // TODO embed reason into retract
        sendJingleMessage("retract", id.with.asBareJid());
        transitionOrThrow(reasonToState(reason));
        this.finish();
    }

    private void prepareSessionInitiate(
            final org.webrtc.SessionDescription webRTCSessionDescription, final State targetState) {
        final SessionDescription sessionDescription =
                SessionDescription.parse(webRTCSessionDescription.description);
        final RtpContentMap rtpContentMap = RtpContentMap.of(sessionDescription, true);
        this.initiatorRtpContentMap = rtpContentMap;
        final ListenableFuture<RtpContentMap> outgoingContentMapFuture =
                encryptSessionInitiate(rtpContentMap);
        Futures.addCallback(
                outgoingContentMapFuture,
                new FutureCallback<RtpContentMap>() {
                    @Override
                    public void onSuccess(final RtpContentMap outgoingContentMap) {
                        sendSessionInitiate(outgoingContentMap, targetState);
                        webRTCWrapper.setIsReadyToReceiveIceCandidates(true);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        failureToInitiateSession(throwable, targetState);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void sendSessionInitiate(final RtpContentMap rtpContentMap, final State targetState) {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": preparing session was too slow. already terminated. nothing to"
                            + " do.");
            return;
        }
        this.transitionOrThrow(targetState);
        final JinglePacket sessionInitiate =
                rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_INITIATE, id.sessionId);
        send(sessionInitiate);
    }

    private ListenableFuture<RtpContentMap> encryptSessionInitiate(
            final RtpContentMap rtpContentMap) {
        if (this.omemoVerification.hasDeviceId()) {
            final ListenableFuture<AxolotlService.OmemoVerifiedPayload<OmemoVerifiedRtpContentMap>>
                    verifiedPayloadFuture =
                            getManager(AxolotlManager.class)
                                    .encrypt(
                                            rtpContentMap,
                                            id.with,
                                            omemoVerification.getDeviceId());
            final ListenableFuture<RtpContentMap> future =
                    Futures.transform(
                            verifiedPayloadFuture,
                            verifiedPayload -> {
                                omemoVerification.setSessionFingerprint(
                                        verifiedPayload.getFingerprint());
                                return verifiedPayload.getPayload();
                            },
                            MoreExecutors.directExecutor());
            if (Config.REQUIRE_RTP_VERIFICATION) {
                return future;
            }
            return Futures.catching(
                    future,
                    AxolotlEncryptionException.class,
                    e -> {
                        LOGGER.warn(
                                connection.getAccount().address
                                        + ": unable to use OMEMO DTLS verification on outgoing"
                                        + " session initiate. falling back",
                                e);
                        return rtpContentMap;
                    },
                    MoreExecutors.directExecutor());
        } else {
            return Futures.immediateFuture(rtpContentMap);
        }
    }

    private void sendSessionTerminate(final Reason reason) {
        sendSessionTerminate(reason, null);
    }

    private void sendSessionTerminate(final Reason reason, final String text) {
        final State previous = this.state;
        final State target = reasonToState(reason);
        transitionOrThrow(target);
        if (previous != State.NULL) {
            writeLogMessage(target);
        }
        final JinglePacket jinglePacket =
                new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
        jinglePacket.setReason(reason, text);
        LOGGER.debug(jinglePacket.toString());
        send(jinglePacket);
        finish();
    }

    private void sendTransportInfo(
            final String contentName, IceUdpTransportInfo.Candidate candidate) {
        final RtpContentMap transportInfo;
        try {
            final RtpContentMap rtpContentMap =
                    isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
            transportInfo = rtpContentMap.transportInfo(contentName, candidate);
        } catch (final Exception e) {
            LOGGER.debug(
                    connection.getAccount().address
                            + ": unable to prepare transport-info from candidate for content="
                            + contentName);
            return;
        }
        final JinglePacket jinglePacket =
                transportInfo.toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        send(jinglePacket);
    }

    private void send(final JinglePacket jinglePacket) {
        jinglePacket.setTo(id.with);
        connection.sendIqPacket(jinglePacket, this::handleIqResponse);
        connection.sendIqPacket(jinglePacket, this::handleIqResponse);
    }

    private synchronized void handleIqResponse(final Iq response) {
        if (response.getType() == Iq.Type.ERROR) {
            handleIqErrorResponse(response);
            return;
        }
        if (response.getType() == Iq.Type.TIMEOUT) {
            handleIqTimeoutResponse(response);
        }
    }

    private void handleIqErrorResponse(final Iq response) {
        Preconditions.checkArgument(response.getType() == Iq.Type.ERROR);
        final var error = response.getError();
        final var errorCondition = error == null ? null : error.getCondition();
        LOGGER.debug(
                connection.getAccount().address
                        + ": received IQ-error from "
                        + response.getFrom()
                        + " in RTP session. "
                        + errorCondition);
        if (isTerminated()) {
            LOGGER.info(
                    connection.getAccount().address
                            + ": ignoring error because session was already terminated");
            return;
        }
        this.webRTCWrapper.close();
        final State target;
        if (Arrays.asList(
                        "service-unavailable",
                        "recipient-unavailable",
                        "remote-server-not-found",
                        "remote-server-timeout")
                .contains(errorCondition.getName())) {
            target = State.TERMINATED_CONNECTIVITY_ERROR;
        } else {
            target = State.TERMINATED_APPLICATION_FAILURE;
        }
        transitionOrThrow(target);
        this.finish();
    }

    private void handleIqTimeoutResponse(final Iq response) {
        Preconditions.checkArgument(response.getType() == Iq.Type.TIMEOUT);
        LOGGER.debug(
                connection.getAccount().address
                        + ": received IQ timeout in RTP session with "
                        + id.with
                        + ". terminating with connectivity error");
        if (isTerminated()) {
            LOGGER.info(
                    connection.getAccount().address
                            + ": ignoring error because session was already terminated");
            return;
        }
        this.webRTCWrapper.close();
        transitionOrThrow(State.TERMINATED_CONNECTIVITY_ERROR);
        this.finish();
    }

    private void terminateWithOutOfOrder(final Iq jinglePacket) {
        LOGGER.debug(connection.getAccount().address + ": terminating session with out-of-order");
        this.webRTCWrapper.close();
        transitionOrThrow(State.TERMINATED_APPLICATION_FAILURE);
        respondWithOutOfOrder(jinglePacket);
        this.finish();
    }

    private void respondWithTieBreak(final Iq jinglePacket) {
        respondWithJingleError(
                jinglePacket, "tie-break", Error.Type.CANCEL, new Condition.Conflict());
    }

    private void respondWithOutOfOrder(final Iq jinglePacket) {
        respondWithJingleError(
                jinglePacket, "out-of-order", Error.Type.WAIT, new Condition.UnexpectedRequest());
    }

    private void respondWithJingleError(
            final Iq original, String jingleCondition, final Error.Type type, Condition condition) {
        // TODO add jingle condition
        connection.sendErrorFor(original, type, condition);
    }

    private void respondOk(final Iq jinglePacket) {
        connection.sendResultFor(jinglePacket);
    }

    public RtpEndUserState getEndUserState() {
        switch (this.state) {
            case NULL:
            case PROPOSED:
            case SESSION_INITIALIZED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.INCOMING_CALL;
                }
            case PROCEED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.ACCEPTING_CALL;
                }
            case SESSION_INITIALIZED_PRE_APPROVED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.CONNECTING;
                }
            case SESSION_ACCEPTED:
                final ContentAddition ca = getPendingContentAddition();
                if (ca != null && ca.direction == ContentAddition.Direction.INCOMING) {
                    return RtpEndUserState.INCOMING_CONTENT_ADD;
                }
                return getPeerConnectionStateAsEndUserState();
            case REJECTED:
            case REJECTED_RACED:
            case TERMINATED_DECLINED_OR_BUSY:
                if (isInitiator()) {
                    return RtpEndUserState.DECLINED_OR_BUSY;
                } else {
                    return RtpEndUserState.ENDED;
                }
            case TERMINATED_SUCCESS:
            case ACCEPTED:
            case RETRACTED:
            case TERMINATED_CANCEL_OR_TIMEOUT:
                return RtpEndUserState.ENDED;
            case RETRACTED_RACED:
                if (isInitiator()) {
                    return RtpEndUserState.ENDED;
                } else {
                    return RtpEndUserState.RETRACTED;
                }
            case TERMINATED_CONNECTIVITY_ERROR:
                return zeroDuration()
                        ? RtpEndUserState.CONNECTIVITY_ERROR
                        : RtpEndUserState.CONNECTIVITY_LOST_ERROR;
            case TERMINATED_APPLICATION_FAILURE:
                return RtpEndUserState.APPLICATION_ERROR;
            case TERMINATED_SECURITY_ERROR:
                return RtpEndUserState.SECURITY_ERROR;
        }
        throw new IllegalStateException(
                String.format("%s has no equivalent EndUserState", this.state));
    }

    private RtpEndUserState getPeerConnectionStateAsEndUserState() {
        final PeerConnection.PeerConnectionState state;
        try {
            state = webRTCWrapper.getState();
        } catch (final WebRTCWrapper.PeerConnectionNotInitialized e) {
            // We usually close the WebRTCWrapper *before* transitioning so we might still
            // be in SESSION_ACCEPTED even though the peerConnection has been torn down
            return RtpEndUserState.ENDING_CALL;
        }
        switch (state) {
            case CONNECTED:
                return RtpEndUserState.CONNECTED;
            case NEW:
            case CONNECTING:
                return RtpEndUserState.CONNECTING;
            case CLOSED:
                return RtpEndUserState.ENDING_CALL;
            default:
                return zeroDuration()
                        ? RtpEndUserState.CONNECTIVITY_ERROR
                        : RtpEndUserState.RECONNECTING;
        }
    }

    public ContentAddition getPendingContentAddition() {
        final RtpContentMap in = this.incomingContentAdd;
        final RtpContentMap out = this.outgoingContentAdd;
        if (out != null) {
            return ContentAddition.of(ContentAddition.Direction.OUTGOING, out);
        } else if (in != null) {
            return ContentAddition.of(ContentAddition.Direction.INCOMING, in);
        } else {
            return null;
        }
    }

    public Set<Media> getMedia() {
        final State current = getState();
        if (current == State.NULL) {
            if (isInitiator()) {
                return Preconditions.checkNotNull(
                        this.proposedMedia, "RTP connection has not been initialized properly");
            }
            throw new IllegalStateException("RTP connection has not been initialized yet");
        }
        if (Arrays.asList(State.PROPOSED, State.PROCEED).contains(current)) {
            return Preconditions.checkNotNull(
                    this.proposedMedia, "RTP connection has not been initialized properly");
        }
        final RtpContentMap localContentMap = getLocalContentMap();
        final RtpContentMap initiatorContentMap = initiatorRtpContentMap;
        if (localContentMap != null) {
            return localContentMap.getMedia();
        } else if (initiatorContentMap != null) {
            return initiatorContentMap.getMedia();
        } else if (isTerminated()) {
            return Collections.emptySet(); // we might fail before we ever got a chance to set media
        } else {
            return Preconditions.checkNotNull(
                    this.proposedMedia, "RTP connection has not been initialized properly");
        }
    }

    public boolean isVerified() {
        final IdentityKey fingerprint = this.omemoVerification.getFingerprint();
        if (fingerprint == null) {
            return false;
        }
        // TODO look up fingerprint trust status;
        return false;
    }

    public boolean addMedia(final Media media) {
        final Set<Media> currentMedia = getMedia();
        if (currentMedia.contains(media)) {
            throw new IllegalStateException(String.format("%s has already been proposed", media));
        }
        // TODO add state protection - can only add while ACCEPTED or so
        LOGGER.debug("adding media: " + media);
        return webRTCWrapper.addTrack(media);
    }

    public synchronized void acceptCall() {
        switch (this.state) {
            case PROPOSED:
                cancelRingingTimeout();
                acceptCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                cancelRingingTimeout();
                acceptCallFromSessionInitialized();
                break;
            case ACCEPTED:
                LOGGER.warn(
                        connection.getAccount().address
                                + ": the call has already been accepted  with another client. UI"
                                + " was just lagging behind");
                break;
            case PROCEED:
            case SESSION_ACCEPTED:
                LOGGER.warn(
                        connection.getAccount().address
                                + ": the call has already been accepted. user probably double"
                                + " tapped the UI");
                break;
            default:
                throw new IllegalStateException("Can not accept call from " + this.state);
        }
    }

    public void notifyPhoneCall() {
        LOGGER.debug("a phone call has just been started. killing jingle rtp connections");
        if (Arrays.asList(State.PROPOSED, State.SESSION_INITIALIZED).contains(this.state)) {
            rejectCall();
        } else {
            endCall();
        }
    }

    public synchronized void rejectCall() {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": received rejectCall() when session has already been terminated."
                            + " nothing to do");
            return;
        }
        switch (this.state) {
            case PROPOSED:
                rejectCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                rejectCallFromSessionInitiate();
                break;
            default:
                throw new IllegalStateException("Can not reject call from " + this.state);
        }
    }

    public synchronized void endCall() {
        if (isTerminated()) {
            LOGGER.warn(
                    connection.getAccount().address
                            + ": received endCall() when session has already been terminated."
                            + " nothing to do");
            return;
        }
        if (isInState(State.PROPOSED) && !isInitiator()) {
            rejectCallFromProposed();
            return;
        }
        if (isInState(State.PROCEED)) {
            if (isInitiator()) {
                retractFromProceed();
            } else {
                rejectCallFromProceed();
            }
            return;
        }
        if (isInitiator()
                && isInState(State.SESSION_INITIALIZED, State.SESSION_INITIALIZED_PRE_APPROVED)) {
            this.webRTCWrapper.close();
            sendSessionTerminate(Reason.CANCEL);
            return;
        }
        if (isInState(State.SESSION_INITIALIZED)) {
            rejectCallFromSessionInitiate();
            return;
        }
        if (isInState(State.SESSION_INITIALIZED_PRE_APPROVED, State.SESSION_ACCEPTED)) {
            this.webRTCWrapper.close();
            sendSessionTerminate(Reason.SUCCESS);
            return;
        }
        if (isInState(
                State.TERMINATED_APPLICATION_FAILURE,
                State.TERMINATED_CONNECTIVITY_ERROR,
                State.TERMINATED_DECLINED_OR_BUSY)) {
            LOGGER.debug("ignoring request to end call because already in state " + this.state);
            return;
        }
        throw new IllegalStateException(
                "called 'endCall' while in state " + this.state + ". isInitiator=" + isInitiator());
    }

    private void retractFromProceed() {
        LOGGER.debug("retract from proceed");
        this.sendJingleMessage("retract");
        closeTransitionLogFinish(State.RETRACTED_RACED);
    }

    private void closeTransitionLogFinish(final State state) {
        this.webRTCWrapper.close();
        transitionOrThrow(state);
        writeLogMessage(state);
        finish();
    }

    private void setupWebRTC(
            final Set<Media> media, final List<PeerConnection.IceServer> iceServers)
            throws WebRTCWrapper.InitializationException {
        getManager(JingleConnectionManager.class).ensureConnectionIsRegistered(this);
        this.webRTCWrapper.setup(this.context, AppRTCAudioManager.SpeakerPhonePreference.of(media));
        this.webRTCWrapper.initializePeerConnection(media, iceServers);
    }

    private void acceptCallFromProposed() {
        transitionOrThrow(State.PROCEED);
        rtpSessionNotification.cancelIncomingCallNotification();
        this.sendJingleMessage("accept", connection.getAccount().address);
        this.sendJingleMessage("proceed");
    }

    private void rejectCallFromProposed() {
        transitionOrThrow(State.REJECTED);
        writeLogMessageMissed();
        rtpSessionNotification.cancelIncomingCallNotification();
        this.sendJingleMessage("reject");
        finish();
    }

    private void rejectCallFromProceed() {
        this.sendJingleMessage("reject");
        closeTransitionLogFinish(State.REJECTED_RACED);
    }

    private void rejectCallFromSessionInitiate() {
        webRTCWrapper.close();
        sendSessionTerminate(Reason.DECLINE);
        rtpSessionNotification.cancelIncomingCallNotification();
    }

    private void sendJingleMessage(final String action) {
        sendJingleMessage(action, id.with);
    }

    private void sendJingleMessage(final String action, final Jid to) {
        final Message messagePacket = new Message();
        messagePacket.setType(Message.Type.CHAT); // we want to carbon copy those
        messagePacket.setTo(to);
        final Element intent =
                messagePacket
                        .addChild(action, Namespace.JINGLE_MESSAGE)
                        .setAttribute("id", id.sessionId);
        if ("proceed".equals(action)) {
            messagePacket.setId(JINGLE_MESSAGE_PROCEED_ID_PREFIX + id.sessionId);
            if (isOmemoEnabled()) {
                final int deviceId = getAccount().getPublicDeviceIdInt();
                final Element device =
                        intent.addChild("device", Namespace.OMEMO_DTLS_SRTP_VERIFICATION);
                device.setAttribute("id", deviceId);
            }
        }
        messagePacket.addChild("store", "urn:xmpp:hints");
        connection.sendMessagePacket(messagePacket);
    }

    private boolean isOmemoEnabled() {
        // TODO look up if omemo is enabled for this chat
        return false;
    }

    private void acceptCallFromSessionInitialized() {
        rtpSessionNotification.cancelIncomingCallNotification();
        sendSessionAccept();
    }

    private synchronized boolean isInState(State... state) {
        return Arrays.asList(state).contains(this.state);
    }

    private boolean transition(final State target) {
        return transition(target, null);
    }

    private synchronized boolean transition(final State target, final Runnable runnable) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            if (runnable != null) {
                runnable.run();
            }
            LOGGER.debug(connection.getAccount().address + ": transitioned into " + target);
            updateEndUserState();
            updateOngoingCallNotification();
            return true;
        } else {
            return false;
        }
    }

    public void transitionOrThrow(final State target) {
        if (!transition(target)) {
            throw new IllegalStateException(
                    String.format("Unable to transition from %s to %s", this.state, target));
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {
        final RtpContentMap rtpContentMap =
                isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
        final IceUdpTransportInfo.Credentials credentials;
        try {
            credentials = rtpContentMap.getCredentials(iceCandidate.sdpMid);
        } catch (final IllegalArgumentException e) {
            LOGGER.debug("ignoring (not sending) candidate: " + iceCandidate, e);
            return;
        }
        final String uFrag = credentials.ufrag;
        final IceUdpTransportInfo.Candidate candidate =
                IceUdpTransportInfo.Candidate.fromSdpAttribute(iceCandidate.sdp, uFrag);
        if (candidate == null) {
            LOGGER.debug("ignoring (not sending) candidate: " + iceCandidate);
            return;
        }
        LOGGER.debug("sending candidate: " + iceCandidate);
        sendTransportInfo(iceCandidate.sdpMid, candidate);
    }

    @Override
    public void onConnectionChange(final PeerConnection.PeerConnectionState newState) {
        LOGGER.debug(
                connection.getAccount().address + ": PeerConnectionState changed to " + newState);
        this.stateHistory.add(newState);
        if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
            this.sessionDuration.start();
            updateOngoingCallNotification();
        } else if (this.sessionDuration.isRunning()) {
            this.sessionDuration.stop();
            updateOngoingCallNotification();
        }

        final boolean neverConnected =
                !this.stateHistory.contains(PeerConnection.PeerConnectionState.CONNECTED);

        if (newState == PeerConnection.PeerConnectionState.FAILED) {
            if (neverConnected) {
                if (isTerminated()) {
                    LOGGER.debug(
                            connection.getAccount().address
                                    + ": not sending session-terminate after connectivity error"
                                    + " because session is already in state "
                                    + this.state);
                    return;
                }
                webRTCWrapper.execute(this::closeWebRTCSessionAfterFailedConnection);
                return;
            } else {
                this.restartIce();
            }
        }
        updateEndUserState();
    }

    private void restartIce() {
        this.stateHistory.clear();
        this.webRTCWrapper.restartIce();
    }

    @Override
    public void onRenegotiationNeeded() {
        this.webRTCWrapper.execute(this::renegotiate);
    }

    private void renegotiate() {
        final SessionDescription sessionDescription;
        try {
            sessionDescription = setLocalSessionDescription();
        } catch (final Exception e) {
            final Throwable cause = Throwables.getRootCause(e);
            LOGGER.debug("failed to renegotiate", cause);
            webRTCWrapper.close();
            sendSessionTerminate(Reason.FAILED_APPLICATION, cause.getMessage());
            return;
        }
        final RtpContentMap rtpContentMap = RtpContentMap.of(sessionDescription, isInitiator());
        final RtpContentMap currentContentMap = getLocalContentMap();
        final boolean iceRestart = currentContentMap.iceRestart(rtpContentMap);
        final RtpContentMap.Diff diff = currentContentMap.diff(rtpContentMap);

        LOGGER.debug(
                connection.getAccount().address
                        + ": renegotiate. iceRestart="
                        + iceRestart
                        + " content id diff="
                        + diff);

        if (diff.hasModifications() && iceRestart) {
            webRTCWrapper.close();
            sendSessionTerminate(
                    Reason.FAILED_APPLICATION,
                    "WebRTC unexpectedly tried to modify content and transport at once");
            return;
        }

        if (iceRestart) {
            initiateIceRestart(rtpContentMap);
            return;
        } else if (diff.isEmpty()) {
            LOGGER.debug(
                    "renegotiation. nothing to do. SignalingState="
                            + this.webRTCWrapper.getSignalingState());
        }

        if (diff.added.size() > 0) {
            modifyLocalContentMap(rtpContentMap);
            sendContentAdd(rtpContentMap, diff.added);
        }
    }

    private void initiateIceRestart(final RtpContentMap rtpContentMap) {
        final RtpContentMap transportInfo = rtpContentMap.transportInfo();
        final Iq jinglePacket =
                transportInfo.toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        LOGGER.debug("initiating ice restart: " + jinglePacket);
        jinglePacket.setTo(id.with);
        connection.sendIqPacket(
                jinglePacket,
                (response) -> {
                    if (response.getType() == Iq.Type.RESULT) {
                        LOGGER.debug("received success to our ice restart");
                        setLocalContentMap(rtpContentMap);
                        webRTCWrapper.setIsReadyToReceiveIceCandidates(true);
                        return;
                    }
                    if (response.getType() == Iq.Type.ERROR) {
                        if (isTieBreak(response)) {
                            LOGGER.debug("received tie-break as result of ice restart");
                            return;
                        }
                        handleIqErrorResponse(response);
                    }
                    if (response.getType() == Iq.Type.TIMEOUT) {
                        handleIqTimeoutResponse(response);
                    }
                });
    }

    private boolean isTieBreak(final Iq response) {
        final Element error = response.findChild("error");
        return error != null && error.hasChild("tie-break", Namespace.JINGLE_ERRORS);
    }

    private void sendContentAdd(final RtpContentMap rtpContentMap, final Collection<String> added) {
        final RtpContentMap contentAdd = rtpContentMap.toContentModification(added);
        this.outgoingContentAdd = contentAdd;
        final Iq jinglePacket =
                contentAdd.toJinglePacket(JinglePacket.Action.CONTENT_ADD, id.sessionId);
        jinglePacket.setTo(id.with);
        connection.sendIqPacket(
                jinglePacket,
                (response) -> {
                    if (response.getType() == Iq.Type.RESULT) {
                        LOGGER.debug(
                                connection.getAccount().address
                                        + ": received ACK to our content-add");
                        return;
                    }
                    if (response.getType() == Iq.Type.ERROR) {
                        if (isTieBreak(response)) {
                            this.outgoingContentAdd = null;
                            LOGGER.debug("received tie-break as result of our content-add");
                            return;
                        }
                        handleIqErrorResponse(response);
                    }
                    if (response.getType() == Iq.Type.TIMEOUT) {
                        handleIqTimeoutResponse(response);
                    }
                });
    }

    private void setLocalContentMap(final RtpContentMap rtpContentMap) {
        if (isInitiator()) {
            this.initiatorRtpContentMap = rtpContentMap;
        } else {
            this.responderRtpContentMap = rtpContentMap;
        }
    }

    private void setRemoteContentMap(final RtpContentMap rtpContentMap) {
        if (isInitiator()) {
            this.responderRtpContentMap = rtpContentMap;
        } else {
            this.initiatorRtpContentMap = rtpContentMap;
        }
    }

    // this method is to be used for content map modifications that modify media
    private void modifyLocalContentMap(final RtpContentMap rtpContentMap) {
        final RtpContentMap activeContents = rtpContentMap.activeContents();
        setLocalContentMap(activeContents);
        this.webRTCWrapper.switchSpeakerPhonePreference(
                AppRTCAudioManager.SpeakerPhonePreference.of(activeContents.getMedia()));
        updateEndUserState();
    }

    private SessionDescription setLocalSessionDescription()
            throws ExecutionException, InterruptedException {
        final org.webrtc.SessionDescription sessionDescription =
                this.webRTCWrapper.setLocalDescription().get();
        return SessionDescription.parse(sessionDescription.description);
    }

    private void closeWebRTCSessionAfterFailedConnection() {
        this.webRTCWrapper.close();
        synchronized (this) {
            if (isTerminated()) {
                LOGGER.debug(
                        connection.getAccount().address
                                + ": no need to send session-terminate after failed connection."
                                + " Other party already did");
                return;
            }
            sendSessionTerminate(Reason.CONNECTIVITY_ERROR);
        }
    }

    public boolean zeroDuration() {
        return this.sessionDuration.elapsed(TimeUnit.NANOSECONDS) <= 0;
    }

    public long getCallDuration() {
        return this.sessionDuration.elapsed(TimeUnit.MILLISECONDS);
    }

    public AppRTCAudioManager getAudioManager() {
        return webRTCWrapper.getAudioManager();
    }

    public boolean isMicrophoneEnabled() {
        return webRTCWrapper.isMicrophoneEnabled();
    }

    public boolean setMicrophoneEnabled(final boolean enabled) {
        return webRTCWrapper.setMicrophoneEnabled(enabled);
    }

    public boolean isVideoEnabled() {
        return webRTCWrapper.isVideoEnabled();
    }

    public void setVideoEnabled(final boolean enabled) {
        webRTCWrapper.setVideoEnabled(enabled);
    }

    public boolean isCameraSwitchable() {
        return webRTCWrapper.isCameraSwitchable();
    }

    public boolean isFrontCamera() {
        return webRTCWrapper.isFrontCamera();
    }

    public ListenableFuture<Boolean> switchCamera() {
        return webRTCWrapper.switchCamera();
    }

    @Override
    public void onAudioDeviceChanged(
            AppRTCAudioManager.AudioDevice selectedAudioDevice,
            Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
        getManager(JingleConnectionManager.class)
                .notifyJingleRtpConnectionUpdate(selectedAudioDevice, availableAudioDevices);
    }

    private void updateEndUserState() {
        final RtpEndUserState endUserState = getEndUserState();
        ToneManager.getInstance(context).transition(isInitiator(), endUserState, getMedia());
        getManager(JingleConnectionManager.class)
                .notifyJingleRtpConnectionUpdate(id.with, id.sessionId, endUserState);
    }

    private void updateOngoingCallNotification() {
        final State state = this.state;
        if (STATES_SHOWING_ONGOING_CALL.contains(state)) {
            final boolean reconnecting;
            if (state == State.SESSION_ACCEPTED) {
                reconnecting =
                        getPeerConnectionStateAsEndUserState() == RtpEndUserState.RECONNECTING;
            } else {
                reconnecting = false;
            }

            // TODO decide what we want to do with ongoing call? create a foreground service of
            // RtpSessionService?

            // xmppConnectionService.setOngoingCall(id, getMedia(), reconnecting);
        } else {
            // xmppConnectionService.removeOngoingCall();
        }
    }

    private void discoverIceServers(final OnIceServersDiscovered onIceServersDiscovered) {
        final var externalServicesFuture = getManager(ExternalDiscoManager.class).getServices();
        Futures.addCallback(
                externalServicesFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Collection<Service> services) {
                        final ImmutableList.Builder<PeerConnection.IceServer> listBuilder =
                                ImmutableList.builder();

                        for (final Service service : services) {
                            final var optionalIceServer = toIceServer(service);
                            if (optionalIceServer.isPresent()) {
                                final var iceServer = optionalIceServer.get();
                                LOGGER.debug("discovered ICE Server: {}", iceServer);
                                listBuilder.add(iceServer);
                            }
                        }

                        final var iceServers = listBuilder.build();
                        if (iceServers.size() == 0) {
                            LOGGER.warn("No ICE server discovered");
                        }
                        onIceServersDiscovered.onIceServersDiscovered(iceServers);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        LOGGER.warn("External services discovery failed", throwable);
                        onIceServersDiscovered.onIceServersDiscovered(Collections.emptyList());
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static Optional<PeerConnection.IceServer> toIceServer(final Service service) {
        final String type = service.getAttribute("type");
        final String host = service.getAttribute("host");
        final Optional<Integer> portOptional = service.getOptionalIntAttribute("port");
        final String transport = service.getAttribute("transport");
        final String username = service.getAttribute("username");
        final String password = service.getAttribute("password");
        if (Strings.isNullOrEmpty(host) || !portOptional.isPresent()) {
            return Optional.absent();
        }
        final int port = portOptional.get();
        if (port < 0 || port > 65535) {
            return Optional.absent();
        }
        if (Arrays.asList("stun", "stuns", "turn", "turns").contains(type)
                && Arrays.asList("udp", "tcp").contains(transport)) {
            if (Arrays.asList("stuns", "turns").contains(type) && "udp".equals(transport)) {
                LOGGER.debug("skipping invalid combination of udp/tls in external services");
                return Optional.absent();
            }
            // TODO Starting on milestone 110, Chromium will perform
            // stricter validation of TURN and STUN URLs passed to the
            // constructor of an RTCPeerConnection. More specifically,
            // STUN URLs will not support a query section, and TURN URLs
            // will support only a transport parameter in their query
            // section.
            final PeerConnection.IceServer.Builder iceServerBuilder =
                    PeerConnection.IceServer.builder(
                            String.format(
                                    "%s:%s:%s?transport=%s",
                                    type, IP.wrapIPv6(host), port, transport));
            iceServerBuilder.setTlsCertPolicy(
                    PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK);
            if (username != null && password != null) {
                iceServerBuilder.setUsername(username);
                iceServerBuilder.setPassword(password);
            } else if (Arrays.asList("turn", "turns").contains(type)) {
                // The WebRTC spec requires throwing an
                // InvalidAccessError when username (from libwebrtc
                // source coder)
                // https://chromium.googlesource.com/external/webrtc/+/master/pc/ice_server_parsing.cc
                LOGGER.debug("skipping {}/{} without username and password", type, transport);
                return Optional.absent();
            }
            return Optional.of(iceServerBuilder.createIceServer());
        } else {
            return Optional.absent();
        }
    }

    private void finish() {
        if (isTerminated()) {
            this.cancelRingingTimeout();
            this.webRTCWrapper.verifyClosed();
            getManager(JingleConnectionManager.class)
                    .setTerminalSessionState(id, getEndUserState(), getMedia());
            getManager(JingleConnectionManager.class).finishConnectionOrThrow(this);
        } else {
            throw new IllegalStateException(
                    String.format("Unable to call finish from %s", this.state));
        }
    }

    private void writeLogMessage(final State state) {
        final long duration = getCallDuration();
        if (state == State.TERMINATED_SUCCESS
                || (state == State.TERMINATED_CONNECTIVITY_ERROR && duration > 0)) {
            writeLogMessageSuccess(duration);
        } else {
            writeLogMessageMissed();
        }
    }

    private void writeLogMessageSuccess(final long duration) {
        this.callLogTransformationBuilder.setMedia(getMedia());
        this.callLogTransformationBuilder.setDuration(Duration.ofMillis(duration));
        this.writeMessage();
    }

    private void writeLogMessageMissed() {
        this.callLogTransformationBuilder.setIsMissedCall();
        this.writeMessage();
    }

    private void writeMessage() {
        final CallLogTransformation callLogTransformation =
                this.callLogTransformationBuilder.build();
        LOGGER.info("writing log message to DB {}", callLogTransformation);
        // TODO write CallLogEntry to DB
    }

    public State getState() {
        return this.state;
    }

    public boolean isTerminated() {
        return TERMINATED.contains(this.state);
    }

    public Optional<VideoTrack> getLocalVideoTrack() {
        return webRTCWrapper.getLocalVideoTrack();
    }

    public Optional<VideoTrack> getRemoteVideoTrack() {
        return webRTCWrapper.getRemoteVideoTrack();
    }

    public EglBase.Context getEglBaseContext() {
        return webRTCWrapper.getEglBaseContext();
    }

    public void setProposedMedia(final Set<Media> media) {
        this.proposedMedia = media;
    }

    public void fireStateUpdate() {
        final RtpEndUserState endUserState = getEndUserState();
        getManager(JingleConnectionManager.class)
                .notifyJingleRtpConnectionUpdate(id.with, id.sessionId, endUserState);
    }

    public boolean isSwitchToVideoAvailable() {
        final boolean prerequisite =
                Media.audioOnly(getMedia())
                        && Arrays.asList(RtpEndUserState.CONNECTED, RtpEndUserState.RECONNECTING)
                                .contains(getEndUserState());
        return prerequisite && remoteHasVideoFeature();
    }

    private boolean remoteHasVideoFeature() {
        return BooleanFutures.isDoneAndTrue(this.remoteHasVideoFeature);
    }

    private interface OnIceServersDiscovered {
        void onIceServersDiscovered(List<PeerConnection.IceServer> iceServers);
    }
}
