# won-debugbot
DebugBot Description tbd

Extracted from won-bot README.md
## Debug Bot

[DebugBotApp](src/main/java/won/bot/app/DebugBotApp.java) can be used to test if connections
can be established with the atoms you are creating and if messages can be sent via those connections. For each atom created by you, the Bot will generate a connection request and a hint messages. Additionally, some actions can be triggered by sending text messages on those connections. Check supported [actions](src/main/java/won/bot/framework/eventbot/action/impl/debugbot/DebugBotIncomingMessageToEventMappingAction.java) for more information.

To run the [DebugBotApp](src/main/java/won/bot/app/DebugBotApp.java), an argument specifying the configuration location is needed, e.g:

    -DWON_CONFIG_DIR=C:/webofneeds/conf.local

Make sure this location contains the relevant property files, and you have specified the values of the properties relevant for the system being tested, i.e.:

- in [node-uri-source.properties](../conf/node-uri-source.properties)
  - won.node.uris - specify values of nodes being tested - the bot will react to atoms published on those nodes
- in [owner.properties](../conf/owner.properties)
  - specify default node data (node.default.host/scheme/port) - the bot will create its own atoms on that node
  - make sure both a path to keystore and truststore (keystore/truststore.location) and their password (keystore/truststore.password) is specified. For additional details on the necessary keys and certificates, refer to the Web of Needs [installation notes](https://github.com/researchstudio-sat/webofneeds/blob/master/documentation/installation-cryptographic-keys-and-certificates.md).

> **NOTE:** Use a separate keystore (and key pair) for your bot, especially if you are running another owner application locally - this will result in the node not delivering messages correctly because the queues used for delivery are defined based on certificates. If multiple applications from the same source share a certificate, there will be errors.

> **NOTE:** For the same reason as above, do not run several bot applications at the same time, - stop one before running another or separate their configurations.

> **NOTE:** Keystore and truststore paths have to be specified, but the files themselves do not have to exist initially, they will be created automatically. If you registered to a node using a different certificate before, the keystore and truststore need to be deleted to be able to register correctly again.

Extracted from bot-skeleton README.md repo
# Web of Needs Bot Skeleton

This skeleton contains an echo bot that reacts to each new atom created on a given node. For each atom, the bot sends a configurable number of contact requests (default is 3) that can be accepted by the user. Within the established chat, the bot echoes all sent messages.

> **NOTE:** Be careful with running more than one bot on a given node instance, as multiple bots may get into infinite loops.

The echo bot is a [Spring Boot Application](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-running-your-application.html).

## Running the bot

### Prerequisites

- [Java 8 JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher installed (openJDK 12 is currently not supported and won't work)
- Maven framework set up

### On the command line

```
cd bot-skeleton
export WON_CONFIG_DIR="$(pwd)/conf"
export WON_NODE_URI="https://hackathonnode.matchat.org/won"
mvn spring-boot:run
```
Now [create an atom](https://hackathon.matchat.org/owner/#!/create) to see the bot in action.

### In Intellij Idea
1. Create a run configuration for the class `won.bot.skeleton.EchoBotApp`
2. Add the environment variables

  * `WON_CONFIG_DIR` pointing to the `conf` directory
  * `WON_NODE_URI` pointing to your node uri (e.g. `https://hackathonnode.matchat.org/won` without quotes)
  
  to your run configuration.
  
3. Run your configuration

If you get a message indicating your keysize is restricted on startup (`JCE unlimited strength encryption policy is not enabled, WoN applications will not work. Please consult the setup guide.`), refer to [Enabling Unlimited Strength Jurisdiction Policy](https://github.com/open-eid/cdoc4j/wiki/Enabling-Unlimited-Strength-Jurisdiction-Policy) to increase the allowed key size.

Now [create an atom](https://hackathon.matchat.org/owner/#!/create) to see the bot in action.

## Start coding

Once the echo bot is running, you can use it as a base for implementing your own application. 

### Prerequisites

- [Java 8 JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher installed (openJDK 12 is currently not supported and won't work)
- Java IDE of choice set up
- Maven framework set up

## Setting up
- Download or clone this repository
- Add config files

Please refer to the general [Bot Readme](https://github.com/researchstudio-sat/webofneeds/blob/master/webofneeds/won-bot/README.md) for more information on Web of Needs Bot applications.

### This Bot is available as a [Docker Container](https://hub.docker.com/r/webofneeds/won-debugbot)
