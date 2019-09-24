package won.bot.debugbot.event;

import won.bot.debugbot.behaviour.BotCommandEvent;
import won.bot.debugbot.enums.HintType;
import won.protocol.model.Connection;

/**
 * User: ypanchenko Date: 26.02.2016
 */
public class HintDebugCommandEvent extends BotCommandEvent {
    private HintType hintType = HintType.ATOM_HINT;

    public HintDebugCommandEvent(final Connection con) {
        super(con);
    }

    public HintDebugCommandEvent(Connection con, HintType hintType) {
        super(con);
        this.hintType = hintType;
    }

    public HintType getHintType() {
        return hintType;
    }
}
