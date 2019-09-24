package won.bot.debugbot.event;

import won.bot.debugbot.behaviour.BotCommandEvent;
import won.protocol.model.Connection;

/**
 * User: ypanchenko Date: 26.02.2016
 */
public class OpenDebugCommandEvent extends BotCommandEvent {
    public OpenDebugCommandEvent(final Connection con) {
        super(con);
    }
}
