/*
 * Copyright 2012 Research Studios Austria Forschungsges.m.b.H. Licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package won.bot.debugbot.action;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import won.bot.debugbot.enums.HintType;
import won.bot.debugbot.event.AtomCreatedEventForDebugHint;
import won.bot.debugbot.event.HintDebugCommandEvent;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.event.AtomSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.exception.WonMessageBuilderException;
import won.protocol.message.WonMessage;
import won.protocol.message.WonMessageBuilder;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.RdfUtils.Pair;
import won.protocol.util.WonRdfUtils;
import won.protocol.util.linkeddata.LinkedDataSource;
import won.protocol.util.linkeddata.WonLinkedDataUtils;

/**
 * BaseEventBotAction connecting two atoms on the specified sockets, or on other compatible sockets, if the Event
 * default sockets if none are specified. Requires an AtomSpecificEvent to run and expects the atomURI from the event to
 * be associated with another atom URI via the botContext.saveToObjectMap method. If the event is a
 * AtomCreatedEventForDebugHint the HintType is extracted from it, otherwise HintType.ATOM_HINT is assumed. If the event
 * is a AtomCreatedEventForDebugHint we send a text message explaining what kind of hint we produced.
 * <ul>
 * <li>If it is HintType.ATOM_HINT, an AtomHintMessage is sent.</li>
 * <li>if it is HintType.SOCKET_HINT, the specified sockets are used (i.e. local/targetSocketType). If those are not
 * specified, RANDOM_SOCKET_HINT is assumed</li>
 * <li>if it is HintType.RANDOM_SOCKET_HINT, the two compatible sockets are selected at random for the hint. If there
 * are no compatible sockets, INCOMPATIBLE_SOCKET_HINT is assumed</li>
 * <li>if it is HintType.INCOMPATIBLE_SOCKET_HINT, a random pair of sockets is selected that is incompatible. If we
 * don't find one, we log an error message.</li>
 * <li>
 * </ul>
 */
public class HintAssociatedAtomAction extends BaseEventBotAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Optional<URI> targetSocketType = Optional.empty();
    private Optional<URI> localSocketType = Optional.empty();
    private final URI matcherURI;

    public HintAssociatedAtomAction(final EventListenerContext eventListenerContext, final URI targetSocketType,
            final URI localSocketType, final URI matcherURI) {
        super(eventListenerContext);
        this.targetSocketType = Optional.of(targetSocketType);
        this.localSocketType = Optional.of(localSocketType);
        this.matcherURI = matcherURI;
    }

    /**
     * @param eventListenerContext
     * @param matcherURI
     */
    public HintAssociatedAtomAction(EventListenerContext eventListenerContext, URI matcherURI) {
        super(eventListenerContext);
        this.matcherURI = matcherURI;
    }

    @Override
    public void doRun(Event event, EventListener executingListener) {
        if (!(event instanceof AtomSpecificEvent)) {
            logger.error("HintAssociatedAtomAction can only handle AtomSpecificEvents");
            return;
        }
        HintType hintType = event instanceof AtomCreatedEventForDebugHint
                ? ((AtomCreatedEventForDebugHint) event).getHintType() : HintType.ATOM_HINT;
        final URI hintTargetAtomUri = ((AtomSpecificEvent) event).getAtomURI();
        final URI hintRecipientAtomUri = getEventListenerContext().getBotContextWrapper()
                .getUriAssociation(hintTargetAtomUri);
        try {
            logger.info("Sending hint for {} and {}", hintTargetAtomUri, hintRecipientAtomUri);
            Optional<WonMessage> msg = createWonMessage(hintRecipientAtomUri, hintTargetAtomUri, 0.9, matcherURI,
                    hintType, event);
            if (msg.isPresent()) {
                logger.debug("Sending Hint message: " + msg.get().toStringForDebug(true));
                getEventListenerContext().getMatcherProtocolAtomServiceClient().hint(hintRecipientAtomUri,
                        hintTargetAtomUri, 0.9, matcherURI, null, msg.get());
            } else {
                logger.warn("could not send hint for " + hintTargetAtomUri + " to " + hintRecipientAtomUri
                        + ": message generation failed ");
            }
        } catch (Exception e) {
            logger.warn("could not send hint for " + hintTargetAtomUri + " to " + hintRecipientAtomUri, e);
        }
    }

    private Optional<WonMessage> createWonMessage(URI hintRecipientAtomURI, URI hintTargetAtomURI, double score,
            URI originator, HintType hintType, Event event) throws WonMessageBuilderException {
        LinkedDataSource linkedDataSource = getEventListenerContext().getLinkedDataSource();
        WonNodeInformationService wonNodeInformationService = getEventListenerContext().getWonNodeInformationService();
        URI recipientWonNode = WonRdfUtils.AtomUtils
                .getWonNodeURIFromAtom(linkedDataSource.getDataForResource(hintRecipientAtomURI), hintRecipientAtomURI);
        if (hintType == HintType.ATOM_HINT) {
            sendMessageIfReactingToDebugCommand(event,
                    "Sending AtomHintMessage to " + hintRecipientAtomURI + " with target " + hintTargetAtomURI + ".");
            return Optional.of(WonMessageBuilder
                    .setMessagePropertiesForHintToAtom(wonNodeInformationService.generateEventURI(recipientWonNode),
                            hintRecipientAtomURI, recipientWonNode, hintTargetAtomURI, originator, score)
                    .build());
        }
        if (hintType == HintType.SOCKET_HINT) {
            if (localSocketType.isPresent() && targetSocketType.isPresent()) {
                Optional<URI> hintRecipientSocket = localSocketType.map(socketType -> WonLinkedDataUtils
                        .getSocketsOfType(hintRecipientAtomURI, socketType, linkedDataSource).stream().findFirst()
                        .orElse(null));
                Optional<URI> targetSocket = targetSocketType.map(socketType -> WonLinkedDataUtils
                        .getSocketsOfType(hintTargetAtomURI, socketType, linkedDataSource).stream().findFirst()
                        .orElse(null));
                if (hintRecipientSocket.isPresent() && targetSocket.isPresent()) {
                    sendMessageIfReactingToDebugCommand(event, "Sending SocketHintMessage to "
                            + hintRecipientSocket.get() + " with target " + targetSocket.get() + ".");
                    return Optional.of(WonMessageBuilder
                            .setMessagePropertiesForHintToSocket(
                                    wonNodeInformationService.generateEventURI(recipientWonNode), hintRecipientAtomURI,
                                    hintRecipientSocket.get(), recipientWonNode, targetSocket.get(), originator, score)
                            .build());
                } else {
                    sendMessageIfReactingToDebugCommand(event,
                            "Default sockets are specified but not supported by the atoms. Falling back to a random compatible socket combination");
                    hintType = HintType.RANDOM_SOCKET_HINT;
                }
            } else {
                sendMessageIfReactingToDebugCommand(event,
                        "No default sockets specified, trying random compatible sockets");
                hintType = HintType.RANDOM_SOCKET_HINT;
            }
        }
        if (hintType == HintType.RANDOM_SOCKET_HINT) {
            Set<Pair<URI>> compatibleSockets = WonLinkedDataUtils.getCompatibleSocketsForAtoms(linkedDataSource,
                    hintRecipientAtomURI, hintTargetAtomURI);
            if (!compatibleSockets.isEmpty()) {
                List<Pair<URI>> shuffledSocketPairs = new ArrayList<>(compatibleSockets);
                Collections.shuffle(shuffledSocketPairs);
                Pair<URI> sockets = shuffledSocketPairs.get(0);
                sendMessageIfReactingToDebugCommand(event, "Sending SocketHintMessage to " + sockets.getFirst()
                        + " with target " + sockets.getSecond() + ".");
                return Optional.of(WonMessageBuilder.setMessagePropertiesForHintToSocket(
                        wonNodeInformationService.generateEventURI(recipientWonNode), hintRecipientAtomURI,
                        sockets.getFirst(), recipientWonNode, sockets.getSecond(), originator, score).build());
            } else {
                sendMessageIfReactingToDebugCommand(event, "No compatible sockets found, trying incompatible sockets");
                hintType = HintType.INCOMPATIBLE_SOCKET_HINT;
            }
        }
        if (hintType == HintType.INCOMPATIBLE_SOCKET_HINT) {
            Set<Pair<URI>> incompatibleSockets = WonLinkedDataUtils.getIncompatibleSocketsForAtoms(linkedDataSource,
                    hintRecipientAtomURI, hintTargetAtomURI);
            if (!incompatibleSockets.isEmpty()) {
                List<Pair<URI>> shuffledSocketPairs = new ArrayList<>(incompatibleSockets);
                Collections.shuffle(shuffledSocketPairs);
                Pair<URI> sockets = shuffledSocketPairs.get(0);
                sendMessageIfReactingToDebugCommand(event, "Sending SocketHintMessage to " + sockets.getFirst()
                        + " with target " + sockets.getSecond() + ".");
                return Optional.of(WonMessageBuilder.setMessagePropertiesForHintToSocket(
                        wonNodeInformationService.generateEventURI(recipientWonNode), hintRecipientAtomURI,
                        sockets.getFirst(), recipientWonNode, sockets.getSecond(), originator, score).build());
            } else {
                sendMessageIfReactingToDebugCommand(event,
                        "No incompatible compatible sockets found. Not sending any hint.");
            }
        }
        logger.info("could not send hint from {} to {}. No suitable sockets found.", hintRecipientAtomURI,
                hintTargetAtomURI);
        return Optional.empty();
    }

    private void sendMessageIfReactingToDebugCommand(Event event, String message) {
        if (event instanceof AtomCreatedEventForDebugHint
                && ((AtomCreatedEventForDebugHint) event).getCause() instanceof HintDebugCommandEvent) {
            AtomCreatedEventForDebugHint e = (AtomCreatedEventForDebugHint) event;
            HintDebugCommandEvent cause = (HintDebugCommandEvent) e.getCause();
            getEventListenerContext().getEventBus().publish(new ConnectionMessageCommandEvent(cause.getCon(), message));
        }
    }
}
