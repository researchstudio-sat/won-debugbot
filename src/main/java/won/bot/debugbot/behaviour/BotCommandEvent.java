package won.bot.debugbot.behaviour;

import won.bot.framework.eventbot.event.BaseAtomAndConnectionSpecificEvent;
import won.protocol.model.Connection;

public abstract class BotCommandEvent extends BaseAtomAndConnectionSpecificEvent {
    public BotCommandEvent(Connection con) {
        super(con);
    }
}
