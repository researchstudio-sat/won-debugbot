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
package won.bot.debugbot.impl;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import won.bot.debugbot.action.AnswerWithElizaAction;
import won.bot.debugbot.action.ConnectWithAssociatedAtomAction;
import won.bot.debugbot.action.CreateDebugAtomAction;
import won.bot.debugbot.action.DebugBotIncomingGenericMessageAction;
import won.bot.debugbot.action.HintAssociatedAtomAction;
import won.bot.debugbot.action.MessageTimingManager;
import won.bot.debugbot.action.OpenConnectionDebugAction;
import won.bot.debugbot.action.PublishSetChattinessEventAction;
import won.bot.debugbot.action.RecordMessageReceivedTimeAction;
import won.bot.debugbot.action.RecordMessageSentTimeAction;
import won.bot.debugbot.action.ReplaceDebugAtomContentAction;
import won.bot.debugbot.action.SendChattyMessageAction;
import won.bot.debugbot.action.SendMessageOnCrawlResultAction;
import won.bot.debugbot.action.SendMessageReportingCrawlResultAction;
import won.bot.debugbot.action.SendNDebugMessagesAction;
import won.bot.debugbot.action.SetChattinessAction;
import won.bot.debugbot.enums.HintType;
import won.bot.debugbot.event.AtomCreatedEventForDebugConnect;
import won.bot.debugbot.event.AtomCreatedEventForDebugHint;
import won.bot.debugbot.event.ConnectDebugCommandEvent;
import won.bot.debugbot.event.HintDebugCommandEvent;
import won.bot.debugbot.event.MessageToElizaEvent;
import won.bot.debugbot.event.ReplaceDebugAtomContentCommandEvent;
import won.bot.debugbot.event.SendNDebugCommandEvent;
import won.bot.debugbot.event.SetCacheEagernessCommandEvent;
import won.bot.debugbot.event.SetChattinessDebugCommandEvent;
import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.action.impl.RandomDelayedAction;
import won.bot.framework.eventbot.behaviour.BotBehaviour;
import won.bot.framework.eventbot.behaviour.CrawlConnectionDataBehaviour;
import won.bot.framework.eventbot.behaviour.EagerlyPopulateCacheBehaviour;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.close.CloseCommandEvent;
import won.bot.framework.eventbot.event.impl.command.close.CloseCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.event.impl.command.deactivate.DeactivateAtomCommandEvent;
import won.bot.framework.eventbot.event.impl.crawlconnection.CrawlConnectionCommandEvent;
import won.bot.framework.eventbot.event.impl.crawlconnection.CrawlConnectionCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.CloseFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.ConnectFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.WonMessageReceivedOnConnectionEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.WonMessageSentOnConnectionEvent;
import won.bot.framework.eventbot.filter.impl.AndFilter;
import won.bot.framework.eventbot.filter.impl.NotFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.extensions.matcher.MatcherBehaviour;
import won.bot.framework.extensions.matcher.MatcherExtension;
import won.bot.framework.extensions.matcher.MatcherExtensionAtomCreatedEvent;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;
import won.bot.framework.extensions.textmessagecommand.TextMessageCommandBehaviour;
import won.bot.framework.extensions.textmessagecommand.TextMessageCommandExtension;
import won.bot.framework.extensions.textmessagecommand.command.EqualsTextMessageCommand;
import won.bot.framework.extensions.textmessagecommand.command.PatternMatcherTextMessageCommand;
import won.bot.framework.extensions.textmessagecommand.command.TextMessageCommand;
import won.protocol.agreement.AgreementProtocolState;
import won.protocol.agreement.effect.MessageEffect;
import won.protocol.model.Connection;
import won.protocol.model.ConnectionState;
import won.protocol.model.SocketType;
import won.protocol.util.WonConversationUtils;
import won.protocol.util.WonRdfUtils;
import won.protocol.util.linkeddata.WonLinkedDataUtils;
import won.protocol.validation.WonConnectionValidator;

/**
 * Bot that reacts to each new atom that is created in the system by creating two atoms, it sends a connect message from
 * one of these atoms, and a hint message for original atom offering match to another of these atoms. Additionally, it
 * reacts to certain commands send via text messages on the connections with the created by the bot atoms.
 */
public class DebugBot extends EventBot implements MatcherExtension, TextMessageCommandExtension, ServiceAtomExtension {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final long CONNECT_DELAY_MILLIS = 0;
    private static final long DELAY_BETWEEN_N_MESSAGES = 1000;
    private static final double CHATTY_MESSAGE_PROBABILITY = 0.1;
    private long registrationMatcherRetryInterval = 30000L;
    private MatcherBehaviour matcherBehaviour;
    private TextMessageCommandBehaviour textMessageCommandBehaviour;
    private ServiceAtomBehaviour serviceAtomBehaviour;
    private URI matcherUri;

    @Override
    public MatcherBehaviour getMatcherBehaviour() {
        return matcherBehaviour;
    }

    @Override
    public TextMessageCommandBehaviour getTextMessageCommandBehaviour() {
        return textMessageCommandBehaviour;
    }

    @Override
    public ServiceAtomBehaviour getServiceAtomBehaviour() {
        return serviceAtomBehaviour;
    }

    @Override
    protected void initializeEventListeners() {
        String welcomeMessage = "Greetings! I am the DebugBot. I "
                + "can simulate multiple other users so you can test things. I understand a few commands. To see which ones, "
                + "type 'usage'.";
        String welcomeHelpMessage = "When connecting with me, you can say 'ignore', or 'deny' to make me ignore or deny requests, and 'wait N' to make me wait N seconds (max 99) before reacting.";
        final EventListenerContext ctx = getEventListenerContext();
        final EventBus bus = getEventBus();
        // define BotCommands for TextMessageCommandBehaviour
        ArrayList<TextMessageCommand> botCommands = new ArrayList<>();
        botCommands.add(new PatternMatcherTextMessageCommand("hint ((random|incompatible) socket)",
                "create a new atom and send me an atom or socket hint (between random or incompatible sockets)",
                Pattern.compile("^hint(\\s+((random|incompatible)\\s+)?socket)?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    matcher.matches();
                    boolean socketHint = matcher.group(1) != null;
                    boolean incompatible = "incompatible".equals(matcher.group(3));
                    boolean random = "random".equals(matcher.group(3));
                    String hintType = socketHint ? incompatible ? "incompatible SocketHintMessage"
                            : random ? "random SocketHintMessage" : "SocketHintMessage" : "AtomHintMessage";
                    bus.publish(new ConnectionMessageCommandEvent(connection,
                            "Ok, I'll create a new atom and send a " + hintType + " to you."));
                    bus.publish(new HintDebugCommandEvent(connection,
                            socketHint
                                    ? incompatible ? HintType.INCOMPATIBLE_SOCKET_HINT
                                            : random ? HintType.RANDOM_SOCKET_HINT : HintType.SOCKET_HINT
                                    : HintType.ATOM_HINT));
                }));
        botCommands.add(new EqualsTextMessageCommand("close", "close the current connection", "close",
                (Connection connection) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection, "Ok, I'll close this connection"));
                    bus.publish(new CloseCommandEvent(connection));
                }));
        botCommands.add(new EqualsTextMessageCommand("modify", "modify the atom's description", "modify",
                (Connection connection) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection, "Ok, I'll change my atom description."));
                    bus.publish(new ReplaceDebugAtomContentCommandEvent(connection));
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("connect",
                "create a new atom and send connection request to it",
                Pattern.compile("^connect$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection,
                            "Ok, I'll create a new atom and make it send a connect to you."));
                    bus.publish(new ConnectDebugCommandEvent(connection));
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("deactivate",
                "deactivate remote atom of the current connection",
                Pattern.compile("^deactivate$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection,
                            "Ok, I'll deactivate this atom. This will close the connection we are currently talking on."));
                    bus.publish(new DeactivateAtomCommandEvent(connection.getAtomURI()));
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("chatty (on|off)",
                "send chat messages spontaneously every now and then? (default: on)",
                Pattern.compile("^chatty(\\s+(on|off))?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    if (matcher.matches()) {
                        String param = matcher.group(2);
                        if ("on".equals(param)) {
                            bus.publish(new ConnectionMessageCommandEvent(connection,
                                    "Ok, I'll send you messages spontaneously from time to time."));
                            bus.publish(new SetChattinessDebugCommandEvent(connection, true));
                        } else if ("off".equals(param)) {
                            bus.publish(new ConnectionMessageCommandEvent(connection,
                                    "Ok, from now on I will be quiet and only respond to your messages."));
                            bus.publish(new SetChattinessDebugCommandEvent(connection, false));
                        }
                    }
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("cache (eager|lazy)", "use lazy or eager RDF cache",
                Pattern.compile("^cache(\\s+(eager|lazy))?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    if (matcher.matches()) {
                        String param = matcher.group(2);
                        if ("eager".equals(param)) {
                            bus.publish(new ConnectionMessageCommandEvent(connection,
                                    "Ok, I'll put any message I receive or send into the RDF cache. This slows down message processing in general, but operations that require crawling connection data will be faster."));
                            bus.publish(new SetCacheEagernessCommandEvent(true));
                        } else if ("lazy".equals(param)) {
                            bus.publish(new ConnectionMessageCommandEvent(connection,
                                    "Ok, I won't put messages I receive or send into the RDF cache. This speeds up message processing in general, but operations that require crawling connection data will be slowed down."));
                            bus.publish(new SetCacheEagernessCommandEvent(false));
                        }
                    }
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("send N",
                "send N messages, one per second. N must be an integer between 1 and 9",
                Pattern.compile("^send ([1-9])$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    matcher.find();
                    String nStr = matcher.group(1);
                    int n = Integer.parseInt(nStr);
                    bus.publish(new SendNDebugCommandEvent(connection, n));
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("validate", "download the connection data and validate it",
                Pattern.compile("^validate$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection,
                            "ok, I'll validate the connection - but I'll need to crawl the connection data first, please be patient."));
                    // initiate crawl behaviour
                    CrawlConnectionCommandEvent command = new CrawlConnectionCommandEvent(connection.getAtomURI(),
                            connection.getConnectionURI());
                    CrawlConnectionDataBehaviour crawlConnectionDataBehaviour = new CrawlConnectionDataBehaviour(ctx,
                            command, Duration.ofSeconds(60));
                    final StopWatch crawlStopWatch = new StopWatch();
                    crawlStopWatch.start("crawl");
                    crawlConnectionDataBehaviour
                            .onResult(new SendMessageReportingCrawlResultAction(ctx, connection, crawlStopWatch));
                    crawlConnectionDataBehaviour.onResult(new SendMessageOnCrawlResultAction(ctx, connection) {
                        @Override
                        protected Model makeSuccessMessage(CrawlConnectionCommandSuccessEvent successEvent) {
                            try {
                                logger.debug("validating data of connection {}", command.getConnectionURI());
                                // TODO: use one validator for all invocations
                                WonConnectionValidator validator = new WonConnectionValidator();
                                StringBuilder message = new StringBuilder();
                                boolean valid = validator.validate(successEvent.getCrawledData(), message);
                                String successMessage = "Connection " + command.getConnectionURI() + " is valid: "
                                        + valid + " " + message.toString();
                                return WonRdfUtils.MessageUtils.textMessage(successMessage);
                            } catch (Exception e) {
                                return WonRdfUtils.MessageUtils.textMessage("Caught exception during validation: " + e);
                            }
                        }
                    });
                    crawlConnectionDataBehaviour.activate();
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("retract (mine|proposal)",
                "retract the last (proposal) message you sent, or the last message I sent",
                Pattern.compile("^retract(\\s+((mine)|(proposal)))?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    matcher.matches();
                    boolean useWrongSender = matcher.group(3) != null;
                    boolean retractProposes = matcher.group(4) != null;
                    String whose = useWrongSender ? "your" : "my";
                    String which = retractProposes ? "proposal " : "";
                    referToEarlierMessages(ctx, bus, connection,
                            "ok, I'll retract " + whose + " latest " + which
                                    + "message - but 'll need to crawl the connection data first, please be patient.",
                            state -> {
                                URI uri = state.getNthLatestMessage(m -> retractProposes
                                        ? (m.isProposesMessage() || m.isProposesToCancelMessage())
                                                && m.getEffects().stream().anyMatch(MessageEffect::isProposes)
                                        : useWrongSender ? m.getSenderAtomURI().equals(connection.getTargetAtomURI())
                                                : m.getSenderAtomURI().equals(connection.getAtomURI()),
                                        0);
                                return uri == null ? Collections.EMPTY_LIST : Collections.singletonList(uri);
                            }, WonRdfUtils.MessageUtils::addRetracts,
                            (Duration queryDuration, AgreementProtocolState state, URI... uris) -> {
                                if (uris == null || uris.length == 0 || uris[0] == null) {
                                    return "Sorry, I cannot retract any messages - I did not find any.";
                                }
                                Optional<String> retractedString = state.getTextMessage(uris[0]);
                                String finalRetractedString = retractedString.map(s -> ", which read, '" + s + "'")
                                        .orElse(", which had no text message");
                                return "Ok, I am hereby retracting " + whose + " message" + finalRetractedString
                                        + " (uri: " + uris[0] + ")." + "\n The query for finding that message took "
                                        + getDurationString(queryDuration) + " seconds.";
                            });
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("reject (yours)",
                "reject the last rejectable message I (you) sent",
                Pattern.compile("^reject(\\s+(yours))?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    matcher.matches();
                    boolean useWrongSender = matcher.group(2) != null;
                    String whose = useWrongSender ? "my" : "your";
                    referToEarlierMessages(ctx, bus, connection, "ok, I'll reject " + whose
                            + " latest rejectable message - but I'll need to crawl the connection data first, please be patient.",
                            state -> {
                                URI uri = state.getLatestProposesOrClaimsMessageSentByAtom(
                                        useWrongSender ? connection.getAtomURI() : connection.getTargetAtomURI());
                                return uri == null ? Collections.EMPTY_LIST : Collections.singletonList(uri);
                            }, WonRdfUtils.MessageUtils::addRejects,
                            (Duration queryDuration, AgreementProtocolState state, URI... uris) -> {
                                if (uris == null || uris.length == 0 || uris[0] == null) {
                                    return "Sorry, I cannot reject any of " + whose
                                            + " messages - I did not find any suitable message.";
                                }
                                Optional<String> retractedString = state.getTextMessage(uris[0]);
                                String finalRetractedString = retractedString.map(s -> ", which read, '" + s + "'")
                                        .orElse(", which had no text message");
                                return "Ok, I am hereby rejecting " + whose + " message" + finalRetractedString
                                        + " (uri: " + uris[0] + ")." + "\n The query for finding that message took "
                                        + getDurationString(queryDuration) + " seconds.";
                            });
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("propose (my|any) (N)",
                "propose one (N, max 9) of my(/your/any) messages for an agreement",
                Pattern.compile("^propose(\\s+((my)|(any))?\\s*([1-9])?)?$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> {
                    matcher.matches();
                    boolean my = matcher.group(3) != null;
                    boolean any = matcher.group(4) != null;
                    int count = matcher.group(5) == null ? 1 : Integer.parseInt(matcher.group(5));
                    boolean allowOwnClauses = any || !my;
                    boolean allowCounterpartClauses = any || my;
                    String whose = allowOwnClauses ? allowCounterpartClauses ? "our" : "my" : allowCounterpartClauses
                            ? "your" : " - sorry, don't know which ones to choose, actually - ";
                    referToEarlierMessages(ctx, bus, connection, "ok, I'll make a proposal containing " + count + " of "
                            + whose
                            + " latest messages as clauses - but I'll need to crawl the connection data first, please be patient.",
                            state -> state.getNLatestMessageUris(m -> {
                                URI ownedAtomUri = connection.getAtomURI();
                                URI targetAtomUri = connection.getTargetAtomURI();
                                return ownedAtomUri != null && ownedAtomUri.equals(m.getSenderAtomURI())
                                        && allowOwnClauses
                                        || targetAtomUri != null && targetAtomUri.equals(m.getSenderAtomURI())
                                                && allowCounterpartClauses;
                            }, count + 1).subList(1, count + 1), WonRdfUtils.MessageUtils::addProposes,
                            (Duration queryDuration, AgreementProtocolState state, URI... uris) -> {
                                if (uris == null || uris.length == 0 || uris[0] == null) {
                                    return "Sorry, I cannot propose the messages - I did not find any.";
                                }
                                // Optional<String> proposedString =
                                // state.getTextMessage(uris[0]);
                                return "Ok, I am hereby making the proposal, containing " + uris.length + " clauses."
                                        + "\n The query for finding the clauses took "
                                        + getDurationString(queryDuration) + " seconds.";
                            });
                }));
        botCommands.add(new PatternMatcherTextMessageCommand("accept",
                "accept the last proposal/claim made (including cancellation proposals)",
                Pattern.compile("^accept$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> referToEarlierMessages(ctx, bus, connection,
                        "ok, I'll accept your latest proposal - but I'll need to crawl the connection data first, please be patient.",
                        state -> {
                            URI uri = state.getLatestPendingProposalOrClaim(Optional.empty(),
                                    Optional.of(connection.getTargetAtomURI()));
                            return uri == null ? Collections.EMPTY_LIST : Collections.singletonList(uri);
                        }, WonRdfUtils.MessageUtils::addAccepts,
                        (Duration queryDuration, AgreementProtocolState state, URI... uris) -> {
                            if (uris == null || uris.length == 0 || uris[0] == null) {
                                return "Sorry, I cannot accept any proposal - I did not find pending proposals";
                            }
                            return "Ok, I am hereby accepting your latest proposal (uri: " + uris[0] + ")."
                                    + "\n The query for finding it took " + getDurationString(queryDuration)
                                    + " seconds.";
                        })));
        botCommands.add(new PatternMatcherTextMessageCommand("cancel",
                "propose to cancel the newest agreement (that wasn't only a cancellation)",
                Pattern.compile("^cancel$", Pattern.CASE_INSENSITIVE),
                (Connection connection, Matcher matcher) -> referToEarlierMessages(ctx, bus, connection,
                        "ok, I'll propose to cancel our latest agreement - but I'll need to crawl the connection data first, please be patient.",
                        state -> {
                            URI uri = state.getLatestAgreement();
                            return uri == null ? Collections.EMPTY_LIST : Collections.singletonList(uri);
                        }, WonRdfUtils.MessageUtils::addProposesToCancel,
                        (Duration queryDuration, AgreementProtocolState state, URI... uris) -> {
                            if (uris == null || uris.length == 0 || uris[0] == null || state == null) {
                                return "Sorry, I cannot propose to cancel any agreement - I did not find any";
                            }
                            return "Ok, I am hereby proposing to cancel our latest agreement (uri: " + uris[0] + ")."
                                    + "\n The query for finding it took " + getDurationString(queryDuration)
                                    + " seconds.";
                        })));
        botCommands.add(new PatternMatcherTextMessageCommand("inject",
                "send a message in this connection that will be forwarded to all other connections we have",
                Pattern.compile("^inject$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
                    bus.publish(new ConnectionMessageCommandEvent(connection,
                            "Ok, I'll send you one message that will be injected into our other connections by your WoN node if the inject permission is granted"));
                    // build a message to be injected into all connections of the receiver atom
                    // (not
                    // controlled by us)
                    Model messageModel = WonRdfUtils.MessageUtils.textMessage("This is the injected message.");
                    // the atom whose connections we want to inject into
                    URI targetAtom = connection.getTargetAtomURI();
                    // we iterate over our atoms and see which of them are connected to the
                    // remote
                    // atom
                    Set<URI> myatoms = ctx.getBotContextWrapper().retrieveAllAtomUris();
                    Set<URI> targetConnections = myatoms.stream()
                            // don't inject into the current connection
                            .filter(uri -> !connection.getAtomURI().equals(uri)).map(uri -> {
                                // for each of my (the bot's) atoms, check if they are
                                // connected to the remote
                                // atom of the current conversation
                                Dataset atomNetwork = WonLinkedDataUtils.getConnectionNetwork(uri,
                                        ctx.getLinkedDataSource());
                                return WonRdfUtils.AtomUtils.getTargetConnectionURIsForTargetAtoms(atomNetwork,
                                        Collections.singletonList(targetAtom), Optional.of(ConnectionState.CONNECTED));
                            }).flatMap(Collection::stream).collect(Collectors.toSet());
                    bus.publish(new ConnectionMessageCommandEvent(connection, messageModel, targetConnections));
                }));
        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();
        // activate TextMessageCommandBehaviour
        textMessageCommandBehaviour = new TextMessageCommandBehaviour(ctx,
                botCommands.toArray(new TextMessageCommand[0]));
        textMessageCommandBehaviour.activate();
        // eagerly cache RDF data
        BotBehaviour eagerlyCacheBehaviour = new EagerlyPopulateCacheBehaviour(ctx);
        eagerlyCacheBehaviour.activate();
        // register listeners for event.impl.command events used to tell the bot to send
        // messages
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();
        // set up matching extension
        matcherBehaviour = new MatcherBehaviour(ctx, "DebugBotMatchingExtension", registrationMatcherRetryInterval);
        matcherBehaviour.activate();
        // filter to prevent reacting to own atoms
        NotFilter noOwnAtomsFilter = getNoOwnAtomsFilter();
        // filter to prevent reacting to serviceAtom<->ownedAtom events;
        NotFilter noInternalServiceAtomEventFilter = getNoInternalServiceAtomEventFilter();
        // // setup for connecting to new atoms
        CreateDebugAtomAction initialConnector = new CreateDebugAtomAction(ctx);
        initialConnector.setIsInitialForConnect(true);
        bus.subscribe(MatcherExtensionAtomCreatedEvent.class, noOwnAtomsFilter, initialConnector);
        // // setup for sending hints to new atoms
        CreateDebugAtomAction initialHinter = new CreateDebugAtomAction(ctx);
        initialHinter.setIsInitialForHint(true);
        bus.subscribe(MatcherExtensionAtomCreatedEvent.class, noOwnAtomsFilter, initialHinter);
        // as soon as the echo atom triggered by debug connect created, connect to
        // original
        bus.subscribe(AtomCreatedEventForDebugConnect.class,
                new RandomDelayedAction(ctx, CONNECT_DELAY_MILLIS, CONNECT_DELAY_MILLIS, 1,
                        new ConnectWithAssociatedAtomAction(ctx, SocketType.ChatSocket.getURI(),
                                SocketType.ChatSocket.getURI(), welcomeMessage + " " + welcomeHelpMessage)));
        // as soon as the echo atom triggered by debug hint command created, hint to
        // original
        bus.subscribe(AtomCreatedEventForDebugHint.class,
                new RandomDelayedAction(ctx, CONNECT_DELAY_MILLIS, CONNECT_DELAY_MILLIS, 1,
                        new HintAssociatedAtomAction(ctx, SocketType.ChatSocket.getURI(),
                                SocketType.ChatSocket.getURI(), matcherUri)));
        // if the original atom wants to connect - always open
        bus.subscribe(ConnectFromOtherAtomEvent.class, noInternalServiceAtomEventFilter,
                new OpenConnectionDebugAction(ctx, welcomeMessage, welcomeHelpMessage),
                new PublishSetChattinessEventAction(ctx, true));
        // if the remote side opens, send a greeting and set to chatty.
        bus.subscribe(ConnectFromOtherAtomEvent.class, noInternalServiceAtomEventFilter,
                new PublishSetChattinessEventAction(ctx, true));
        // filter to prevent reacting to message Commands
        NotFilter noTextMessageCommandsFilter = getNoTextMessageCommandFilter();
        bus.subscribe(ConnectFromOtherAtomEvent.class,
                new AndFilter(noTextMessageCommandsFilter, noInternalServiceAtomEventFilter),
                new DebugBotIncomingGenericMessageAction(ctx));
        // if the bot receives a text message - try to map the command of the text
        // message to a DebugEvent
        bus.subscribe(MessageFromOtherAtomEvent.class, noTextMessageCommandsFilter,
                new DebugBotIncomingGenericMessageAction(ctx));
        bus.subscribe(CloseCommandSuccessEvent.class, new PublishSetChattinessEventAction(ctx, false));
        // react to close event: set connection to not chatty
        bus.subscribe(CloseFromOtherAtomEvent.class, new PublishSetChattinessEventAction(ctx, false));
        MessageTimingManager timingManager = new MessageTimingManager(ctx);
        // on every actEvent there is a chance we send a chatty message
        bus.subscribe(ActEvent.class,
                new SendChattyMessageAction(ctx, CHATTY_MESSAGE_PROBABILITY, timingManager,
                        DebugBotIncomingGenericMessageAction.RANDOM_MESSAGES,
                        DebugBotIncomingGenericMessageAction.LAST_MESSAGES));
        // process eliza messages with eliza
        bus.subscribe(MessageToElizaEvent.class, new AnswerWithElizaAction(ctx));
        // remember when we sent the last message
        bus.subscribe(WonMessageSentOnConnectionEvent.class, new RecordMessageSentTimeAction(ctx, timingManager));
        // remember when we got the last message
        bus.subscribe(WonMessageReceivedOnConnectionEvent.class,
                new RecordMessageReceivedTimeAction(ctx, timingManager));
        // initialize the sent timestamp when the connect message is received
        bus.subscribe(ConnectFromOtherAtomEvent.class, new RecordMessageSentTimeAction(ctx, timingManager));
        // Usage Command Event Subscriptions:
        bus.subscribe(ReplaceDebugAtomContentCommandEvent.class, new ReplaceDebugAtomContentAction(ctx));
        bus.subscribe(SendNDebugCommandEvent.class, new SendNDebugMessagesAction(ctx, DELAY_BETWEEN_N_MESSAGES,
                DebugBotIncomingGenericMessageAction.N_MESSAGES));
        // react to the hint and connect commands by creating an atom (it will fire
        // correct atom created for connect/hint
        // events)
        CreateDebugAtomAction atomCreatorAction = new CreateDebugAtomAction(ctx);
        bus.subscribe(HintDebugCommandEvent.class, atomCreatorAction);
        bus.subscribe(ConnectDebugCommandEvent.class, atomCreatorAction);
        // set the chattiness of the connection
        bus.subscribe(SetChattinessDebugCommandEvent.class, new SetChattinessAction(ctx));
        // react to a bot command activating/deactivating eager caching
        bus.subscribe(SetCacheEagernessCommandEvent.class, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener executingListener) throws Exception {
                if (event instanceof SetCacheEagernessCommandEvent) {
                    if (((SetCacheEagernessCommandEvent) event).isEager()) {
                        eagerlyCacheBehaviour.activate();
                    } else {
                        eagerlyCacheBehaviour.deactivate();
                    }
                }
            }
        });
    }

    /***********************************************************************************
     * Mini framework for allowing the bot to refer to earlier messages while trying to avoid code duplication
     ***********************************************************************************/
    private interface MessageFinder {
        List<URI> findMessages(AgreementProtocolState state);
    }

    private interface MessageReferrer {
        Model referToMessages(Model messageModel, URI... targetUris);
    }

    private interface TextMessageMaker {
        String makeTextMessage(Duration queryDuration, AgreementProtocolState state, URI... uris);
    }

    private void referToEarlierMessages(EventListenerContext ctx, EventBus bus, Connection con,
            String crawlAnnouncement, MessageFinder messageFinder, MessageReferrer messageReferrer,
            TextMessageMaker textMessageMaker) {
        bus.publish(new ConnectionMessageCommandEvent(con, crawlAnnouncement));
        // initiate crawl behaviour
        CrawlConnectionCommandEvent command = new CrawlConnectionCommandEvent(con.getAtomURI(), con.getConnectionURI());
        CrawlConnectionDataBehaviour crawlConnectionDataBehaviour = new CrawlConnectionDataBehaviour(ctx, command,
                Duration.ofSeconds(60));
        final StopWatch crawlStopWatch = new StopWatch();
        crawlStopWatch.start("crawl");
        AgreementProtocolState state = WonConversationUtils.getAgreementProtocolState(con.getConnectionURI(),
                ctx.getLinkedDataSource());
        crawlStopWatch.stop();
        Duration crawlDuration = Duration.ofMillis(crawlStopWatch.getLastTaskTimeMillis());
        getEventListenerContext().getEventBus()
                .publish(new ConnectionMessageCommandEvent(con,
                        "Finished crawl in " + getDurationString(crawlDuration) + " seconds. The dataset has "
                                + state.getConversationDataset().asDatasetGraph().size() + " rdf graphs."));
        Model messageModel = makeReferringMessage(state, messageFinder, messageReferrer, textMessageMaker);
        getEventListenerContext().getEventBus().publish(new ConnectionMessageCommandEvent(con, messageModel));
        crawlConnectionDataBehaviour.activate();
    }

    private Model makeReferringMessage(AgreementProtocolState state, MessageFinder messageFinder,
            MessageReferrer messageReferrer, TextMessageMaker textMessageMaker) {
        int origPrio = Thread.currentThread().getPriority();
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        StopWatch queryStopWatch = new StopWatch();
        queryStopWatch.start("query");
        List<URI> targetUris = messageFinder.findMessages(state);
        URI[] targetUriArray = targetUris.toArray(new URI[0]);
        queryStopWatch.stop();
        Thread.currentThread().setPriority(origPrio);
        Duration queryDuration = Duration.ofMillis(queryStopWatch.getLastTaskTimeMillis());
        Model messageModel = WonRdfUtils.MessageUtils
                .textMessage(textMessageMaker.makeTextMessage(queryDuration, state, targetUriArray));
        return messageReferrer.referToMessages(messageModel, targetUriArray);
    }

    private String getDurationString(Duration queryDuration) {
        return new DecimalFormat("###.##").format(queryDuration.toMillis() / 1000d);
    }

    // BEAN SETTER
    public void setMatcherUri(final URI matcherUri) {
        this.matcherUri = matcherUri;
    }

    public void setRegistrationMatcherRetryInterval(final int registrationMatcherRetryInterval) {
        this.registrationMatcherRetryInterval = registrationMatcherRetryInterval;
    }
}
