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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.event.AtomSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.exception.WonMessageBuilderException;
import won.protocol.message.WonMessage;
import won.protocol.message.builder.WonMessageBuilder;
import won.protocol.util.RdfUtils.Pair;
import won.protocol.util.linkeddata.LinkedDataSource;
import won.protocol.util.linkeddata.WonLinkedDataUtils;

/**
 * BaseEventBotAction connecting two atoms on the specified sockets or on two other, compatible sockets. Requires an
 * AtomSpecificEvent to run and expeects the atomURI from the event to be associated with another atom URI via the
 * botContext.saveToObjectMap method.
 */
public class ConnectWithAssociatedAtomAction extends BaseEventBotAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Optional<URI> targetSocketType = Optional.empty();
    private Optional<URI> localSocketType = Optional.empty();
    private final String welcomeMessage;

    public ConnectWithAssociatedAtomAction(final EventListenerContext eventListenerContext, final URI targetSocketType,
            final URI localSocketType, String welcomeMessage) {
        super(eventListenerContext);
        Objects.requireNonNull(targetSocketType);
        Objects.requireNonNull(localSocketType);
        this.targetSocketType = Optional.of(targetSocketType);
        this.localSocketType = Optional.of(localSocketType);
        this.welcomeMessage = welcomeMessage;
    }

    @Override
    public void doRun(Event event, EventListener executingListener) {
        if (!(event instanceof AtomSpecificEvent)) {
            logger.error("ConnectWithAssociatedAtomAction can only handle AtomSpecificEvents");
            return;
        }
        final URI myAtomUri = ((AtomSpecificEvent) event).getAtomURI();
        final URI targetAtomUri = getEventListenerContext().getBotContextWrapper().getUriAssociation(myAtomUri);
        try {
            Optional<WonMessage> msg = createWonMessage(myAtomUri, targetAtomUri);
            if (msg.isPresent()) {
                getEventListenerContext().getWonMessageSender().prepareAndSendMessage(msg.get());
            } else {
                logger.info("could not connect " + myAtomUri + " and " + targetAtomUri + ": no suitable sockets found");
            }
        } catch (Exception e) {
            logger.warn("could not connect " + myAtomUri + " and " + targetAtomUri, e);
        }
    }

    private Optional<WonMessage> createWonMessage(URI fromUri, URI toUri) throws WonMessageBuilderException {
        LinkedDataSource linkedDataSource = getEventListenerContext().getLinkedDataSource();
        if (localSocketType.isPresent() && targetSocketType.isPresent()) {
            URI localSocket = localSocketType
                    .map(socketType -> WonLinkedDataUtils.getSocketsOfType(fromUri, socketType, linkedDataSource)
                            .stream().findFirst())
                    .orElseThrow(() -> new IllegalStateException("No socket found to connect on " + fromUri)).get();
            URI targetSocket = targetSocketType
                    .map(socketType -> WonLinkedDataUtils.getSocketsOfType(toUri, socketType, linkedDataSource).stream()
                            .findFirst())
                    .orElseThrow(() -> new IllegalStateException("No socket found to connect on " + fromUri)).get();
            return Optional.of(WonMessageBuilder.connect().sockets()/**/.sender(localSocket)/**/.recipient(targetSocket)
                    .content().text(welcomeMessage).build());
        }
        // no sockets specified or specified sockets not supported. try a random
        // compatibly pair
        Set<Pair<URI>> compatibleSockets = WonLinkedDataUtils.getCompatibleSocketsForAtoms(linkedDataSource, fromUri,
                toUri);
        if (!compatibleSockets.isEmpty()) {
            List<Pair<URI>> shuffledSocketPairs = new ArrayList<>(compatibleSockets);
            Collections.shuffle(shuffledSocketPairs);
            Pair<URI> sockets = shuffledSocketPairs.get(0);
            return Optional.of(WonMessageBuilder.connect().sockets()/**/.sender(sockets.getFirst())
                    /**/.recipient(sockets.getSecond()).content().text(welcomeMessage).build());
        }
        return Optional.empty();
    }
}
