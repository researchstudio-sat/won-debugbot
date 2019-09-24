package won.bot.debugbot.event;

import won.bot.debugbot.behaviour.BotCommandEvent;
import won.protocol.model.Connection;

/**
 * Debugbot command instructing the bot to replace the atom content.
 */
public class ReplaceDebugAtomContentCommandEvent extends BotCommandEvent {
    public ReplaceDebugAtomContentCommandEvent(final Connection con) {
        super(con);
    }
}
