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

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import won.bot.debugbot.enums.HintType;
import won.bot.debugbot.event.AtomCreatedEventForDebugConnect;
import won.bot.debugbot.event.AtomCreatedEventForDebugHint;
import won.bot.debugbot.event.ConnectDebugCommandEvent;
import won.bot.debugbot.event.HintDebugCommandEvent;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.action.impl.counter.Counter;
import won.bot.framework.eventbot.action.impl.counter.CounterImpl;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.AtomCreationFailedEvent;
import won.bot.framework.eventbot.event.AtomSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.atomlifecycle.AtomCreatedEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.extensions.matcher.MatcherExtensionAtomCreatedEvent;
import won.protocol.message.WonMessage;
import won.protocol.model.SocketType;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.Prefixer;
import won.protocol.util.RdfUtils;
import won.protocol.util.WonRdfUtils;
import won.protocol.vocabulary.WONMATCH;

/**
 * Creates an atom with the specified Chat, Buddy and Holdable Sockets. If no socket is specified, the chatSocket will
 * be used.
 */
public class CreateDebugAtomAction extends AbstractCreateAtomAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Counter counter = new CounterImpl("DebugAtomsCounter");
    private boolean isInitialForHint;
    private boolean isInitialForConnect;

    public CreateDebugAtomAction(final EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    @Override
    protected void doRun(Event event, EventListener executingListener) throws Exception {
        String replyText;
        URI reactingToAtomUriTmp;
        Dataset atomDataset = null;
        if (event instanceof AtomSpecificEvent) {
            reactingToAtomUriTmp = ((AtomSpecificEvent) event).getAtomURI();
        } else {
            logger.warn("could not process non-atom specific event {}", event);
            return;
        }
        if (event instanceof MatcherExtensionAtomCreatedEvent) {
            atomDataset = ((MatcherExtensionAtomCreatedEvent) event).getAtomData();
        } else if (event instanceof HintDebugCommandEvent) {
            reactingToAtomUriTmp = ((HintDebugCommandEvent) event).getTargetAtomURI();
        } else if (event instanceof ConnectDebugCommandEvent) {
            reactingToAtomUriTmp = ((ConnectDebugCommandEvent) event).getTargetAtomURI();
        } else {
            logger.error("CreateEchoAtomWithSocketsAction cannot handle " + event.getClass().getName());
            return;
        }
        final URI reactingToAtomUri = reactingToAtomUriTmp;
        String titleString = null;
        boolean createAtom = true;
        if (atomDataset != null) {
            DefaultAtomModelWrapper atomModelWrapper = new DefaultAtomModelWrapper(atomDataset);
            titleString = atomModelWrapper.getSomeTitleFromIsOrAll("en", "de");
            createAtom = atomModelWrapper.flag(WONMATCH.UsedForTesting) && !atomModelWrapper.flag(WONMATCH.NoHintForMe);
        }
        if (!createAtom)
            return; // if create atom is false do not continue the debug atom creation
        if (titleString != null) {
            if (isInitialForConnect) {
                replyText = "Debugging with initial connect: " + titleString;
            } else if (isInitialForHint) {
                replyText = "Debugging with initial hint: " + titleString;
            } else {
                replyText = "Debugging: " + titleString;
            }
        } else {
            replyText = "Debug Atom No. " + counter.increment();
        }
        EventListenerContext ctx = getEventListenerContext();
        WonNodeInformationService wonNodeInformationService = ctx.getWonNodeInformationService();
        EventBus bus = ctx.getEventBus();
        final URI wonNodeUri = ctx.getNodeURISource().getNodeURI();
        final URI atomURI = wonNodeInformationService.generateAtomURI(wonNodeUri);
        DefaultAtomModelWrapper atomModelWrapper = new DefaultAtomModelWrapper(atomURI);
        atomModelWrapper.setTitle(replyText);
        atomModelWrapper.setDescription("This is an atom automatically created by the DebugBot.");
        atomModelWrapper.setSeeksTitle(replyText);
        atomModelWrapper.setSeeksDescription("This is an atom automatically created by the DebugBot.");
        atomModelWrapper.addSocket(atomURI + "#chatSocket", SocketType.ChatSocket.getURI().toString());
        atomModelWrapper.addSocket(atomURI + "#holdableSocket", SocketType.HoldableSocket.getURI().toString());
        atomModelWrapper.addSocket(atomURI + "#buddySocket", SocketType.BuddySocket.getURI().toString());

        final Dataset debugAtomDataset = atomModelWrapper.copyDatasetWithoutSysinfo();
        final Event origEvent = event;
        logger.debug("creating atom on won node {} with content {} ", wonNodeUri,
                StringUtils.abbreviate(RdfUtils.toString(Prefixer.setPrefixes(debugAtomDataset)), 150));
        WonMessage createAtomMessage = createWonMessage(atomURI, debugAtomDataset);
        createAtomMessage = getEventListenerContext().getWonMessageSender().prepareMessage(createAtomMessage);
        // remember the atom URI so we can react to success/failure responses
        ctx.getBotContextWrapper().rememberAtomUri(atomURI);
        EventListener successCallback = event12 -> {
            logger.debug("atom creation successful, new atom URI is {}", atomURI);
            // save the mapping between the original and the reaction in to the context.
            getEventListenerContext().getBotContextWrapper().addUriAssociation(reactingToAtomUri, atomURI);
            if (origEvent instanceof HintDebugCommandEvent || isInitialForHint) {
                HintType hintType = HintType.RANDOM_SOCKET_HINT; // default: hint to random compatible sockets
                if (origEvent instanceof HintDebugCommandEvent) {
                    hintType = ((HintDebugCommandEvent) origEvent).getHintType();
                }
                bus.publish(
                        new AtomCreatedEventForDebugHint(origEvent, atomURI, wonNodeUri, debugAtomDataset, hintType));
            } else if ((origEvent instanceof ConnectDebugCommandEvent) || isInitialForConnect) {
                bus.publish(new AtomCreatedEventForDebugConnect(atomURI, wonNodeUri, debugAtomDataset, null));
            } else {
                bus.publish(new AtomCreatedEvent(atomURI, wonNodeUri, debugAtomDataset, null));
            }
        };
        EventListener failureCallback = event1 -> {
            String textMessage = WonRdfUtils.MessageUtils
                    .getTextMessage(((FailureResponseEvent) event1).getFailureMessage());
            logger.debug("atom creation failed for atom URI {}, original message URI {}: {}", atomURI,
                    ((FailureResponseEvent) event1).getOriginalMessageURI(), textMessage);
            ctx.getBotContextWrapper().removeAtomUri(atomURI);
            bus.publish(new AtomCreationFailedEvent(wonNodeUri));
        };
        EventBotActionUtils.makeAndSubscribeResponseListener(createAtomMessage, successCallback, failureCallback, ctx);
        logger.debug("registered listeners for response to message URI {}", createAtomMessage.getMessageURI());
        ctx.getWonMessageSender().sendMessage(createAtomMessage);
        logger.debug("atom creation message sent with message URI {}", createAtomMessage.getMessageURI());
    }

    public void setIsInitialForHint(final boolean isInitialForHint) {
        this.isInitialForHint = isInitialForHint;
    }

    public void setIsInitialForConnect(final boolean isInitialForConnect) {
        this.isInitialForConnect = isInitialForConnect;
    }
}
