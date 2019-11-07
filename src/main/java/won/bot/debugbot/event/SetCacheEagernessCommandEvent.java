package won.bot.debugbot.event;

import won.bot.framework.eventbot.event.BaseEvent;

public class SetCacheEagernessCommandEvent extends BaseEvent {
    private final boolean eager;

    public SetCacheEagernessCommandEvent(boolean eager) {
        this.eager = eager;
    }

    public boolean isEager() {
        return eager;
    }
}
