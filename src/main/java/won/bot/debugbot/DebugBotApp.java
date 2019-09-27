package won.bot.debugbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import won.bot.framework.bot.utils.BotUtils;

@SpringBootConfiguration
@PropertySource("classpath:application.properties")
@ImportResource("classpath:/spring/app/botApp.xml")
public class DebugBotApp {
    public static void main(String[] args) {
        if (!BotUtils.isValidRunConfig()) {
            System.exit(1);
        }

        //SpringApplication app = new SpringApplication("classpath:/spring/app/botApp.xml");
        SpringApplication app = new SpringApplication(DebugBotApp.class);
        app.setWebEnvironment(false);
        app.run(args);
        // ConfigurableApplicationContext applicationContext = app.run(args);
        // Thread.sleep(5*60*1000);
        // app.exit(applicationContext);
    }
}
