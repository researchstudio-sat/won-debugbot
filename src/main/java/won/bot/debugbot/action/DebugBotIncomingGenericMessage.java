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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.debugbot.event.*;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.BaseAtomAndConnectionSpecificEvent;
import won.bot.framework.eventbot.event.ConnectionSpecificEvent;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.MessageEvent;
import won.bot.framework.eventbot.event.impl.command.connectionmessage.ConnectionMessageCommandEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.message.WonMessage;
import won.protocol.model.Connection;
import won.protocol.util.WonRdfUtils;

import java.lang.invoke.MethodHandles;

/**
 * Listener that reacts to incoming messages, creating internal bot events for
 * them
 */
public class DebugBotIncomingGenericMessage extends BaseEventBotAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final String[] N_MESSAGES = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten"};
    public static final String[] RANDOM_MESSAGES = {"Is there anything I can do for you?",
            "Did you read the news today?", "By the way, don't you just love the weather these days?",
            "Type 'usage' to see what I can do for you!", "I think I might see a movie tonight",};
    public static final String[] LAST_MESSAGES = {"?", "Are you still there?", "Gone?", "... cu later, I guess?",
            "Do you still require my services? You can use the 'close' command, you know...", "Ping?"};

    public DebugBotIncomingGenericMessage(EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    @Override
    protected void doRun(final Event event, EventListener executingListener) throws Exception {
        if (event instanceof BaseAtomAndConnectionSpecificEvent) {
            handleTextMessageEvent((ConnectionSpecificEvent) event);
        }
    }

    private void handleTextMessageEvent(final ConnectionSpecificEvent messageEvent) {
        if (messageEvent instanceof MessageEvent) {
            EventListenerContext ctx = getEventListenerContext();
            EventBus bus = ctx.getEventBus();
            Connection con = ((BaseAtomAndConnectionSpecificEvent) messageEvent).getCon();
            WonMessage msg = ((MessageEvent) messageEvent).getWonMessage();
            String message = extractTextMessageFromWonMessage(msg);
            try {
                if (message == null) {
                    bus.publish(new ConnectionMessageCommandEvent(con, "Whatever you sent me there, it was not a normal text message. I'm expecting a <message> con:text \"Some text\" triple in that message."));
                } else {
                    //TODO: REACT TO NON COMMAND EVENTS ONLY?
                    logger.trace("Handling Message to eliza...");
                    bus.publish(new MessageToElizaEvent(con, message));
                }
            } catch (Exception e) {
                // error: send an error message
                bus.publish(new ConnectionMessageCommandEvent(con, "Did not understand your command '" + message
                        + "': " + e.getClass().getSimpleName() + ":" + e.getMessage()));
            }
        }
    }

    private String extractTextMessageFromWonMessage(WonMessage wonMessage) {
        if (wonMessage == null)
            return null;
        String message = WonRdfUtils.MessageUtils.getTextMessage(wonMessage);
        return StringUtils.trim(message);
    }
}
