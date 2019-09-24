package won.bot.debugbot.event;

import won.bot.debugbot.behaviour.BotCommandEvent;
import won.bot.framework.eventbot.event.BaseAtomAndConnectionSpecificEvent;
import won.protocol.model.Connection;

/**
 * User: ypanchenko Date: 26.02.2016
 */
public class ConnectDebugCommandEvent extends BotCommandEvent {
    public ConnectDebugCommandEvent(final Connection con) {
        super(con);
    }
}
