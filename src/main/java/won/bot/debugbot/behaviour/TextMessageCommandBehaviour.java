package won.bot.debugbot.behaviour;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.impl.wonmessage.SendMessageAction;
import won.bot.framework.eventbot.behaviour.BotBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.impl.wonmessage.MessageFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.OpenFromOtherAtomEvent;
import won.protocol.model.Connection;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextMessageCommandBehaviour extends BotBehaviour {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final List<BotCommand> commands;
    private final String usageCommandMessage;

    /**
     *
     * @param context
     * @param commands
     */
    public TextMessageCommandBehaviour(EventListenerContext context, BotCommand... commands) {
        super(context);
        final EventBus bus = context.getEventBus();
        BotCommand usageCommand = new BotCommand("usage", "display this message", Pattern.compile("^usage|\\?|help|debug$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
            bus.publish(new UsageCommandEvent(connection));
        });
        this.commands = new ArrayList<>();
        this.commands.add(usageCommand);
        this.commands.addAll(Arrays.asList(commands));

        this.usageCommandMessage = getUsageCommandMessage();
    }

    /**
     *
     * @param context
     * @param name
     * @param commands
     */
    public TextMessageCommandBehaviour(EventListenerContext context, String name, BotCommand... commands) {
        super(context, name);
        final EventBus bus = context.getEventBus();
        BotCommand usageCommand = new BotCommand("usage", "display this message", Pattern.compile("^usage|\\?|help|debug$", Pattern.CASE_INSENSITIVE), (Connection connection, Matcher matcher) -> {
            bus.publish(new UsageCommandEvent(connection));
        });
        this.commands = new ArrayList<>();
        this.commands.add(usageCommand);
        this.commands.addAll(Arrays.asList(commands));

        this.usageCommandMessage = getUsageCommandMessage();
    }

    @Override
    protected void onActivate(Optional<Object> optional) {
        logger.debug("activating TextMessageCommandBehaviour");
        EventListenerContext ctx = this.context;
        EventBus bus = ctx.getEventBus();

        TextMessageCommandExecutor textMessageUsageCommandChecker = new TextMessageCommandExecutor(context, commands);
        this.subscribeWithAutoCleanup(OpenFromOtherAtomEvent.class, textMessageUsageCommandChecker);
        this.subscribeWithAutoCleanup(MessageFromOtherAtomEvent.class, textMessageUsageCommandChecker);

        // react to usage command event
        this.subscribeWithAutoCleanup(UsageCommandEvent.class, new SendMessageAction(ctx, this.usageCommandMessage));
    }

    private String getUsageCommandMessage() {
        StringBuilder usageCommandsString = new StringBuilder("# Usage:\n");

        for(BotCommand command : commands) {
            usageCommandsString
                    .append("* ")
                    .append(command.toString())
                    .append("\n");
        }
        return usageCommandsString.toString();
    }

    public boolean isMatchingBotCommand(String message) {
        if(message != null) {
            for (BotCommand command : commands) {
                if (command.matchesCommand(message)) {
                    return true;
                }
            }
        }
        return false;
    }
}
