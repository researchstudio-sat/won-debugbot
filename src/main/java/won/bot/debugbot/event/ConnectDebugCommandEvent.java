package won.bot.debugbot.event;

import won.protocol.model.Connection;

/**
 * User: ypanchenko Date: 26.02.2016
 */
public class ConnectDebugCommandEvent extends DebugCommandEvent {
    public ConnectDebugCommandEvent(final Connection con) {
        super(con);
    }
}
