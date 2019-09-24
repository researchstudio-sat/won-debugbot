package won.bot.debugbot.behaviour;

import won.protocol.model.Connection;

/**
 * User: ypanchenko Date: 26.02.2016
 */
public class UsageCommandEvent extends BotCommandEvent {
    public UsageCommandEvent(final Connection con) {
        super(con);
    }
}
