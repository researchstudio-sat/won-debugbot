# Debug Bot [![docker](https://img.shields.io/docker/pulls/webofneeds/won-debugbot?style=flat-square)](https://hub.docker.com/r/webofneeds/won-debugbot)

This bot can be used to test if connections can be established with the atoms you are creating and if messages can be sent via those connections. For each atom created by you, the Bot will generate a connection request and a random socket hint message. Additionally, some actions can be triggered by sending text messages on those connections. Check supported [actions](https://github.com/researchstudio-sat/won-debugbot/tree/master/src/main/java/won/bot/debugbot/action/DebugBotIncomingMessageToEventMappingAction.java) for more information.

The Debug Bot is a [Spring Boot Application](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-running-your-application.html).

## Running the bot

### Prerequisites

- [Java 8 JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) or higher installed (openJDK 12 is currently not supported and won't work)
- Maven framework set up

### On the command line

```
cd won-debugbot
export WON_NODE_URI="https://hackathonnode.matchat.org/won"
mvn clean package
java -jar target/bot.jar
```

### In Intellij Idea
1. Create a run configuration for the class `won.bot.skeleton.EchoBotApp`
2. Add the environment variables

  * `WON_NODE_URI` pointing to your node uri (e.g. `https://hackathonnode.matchat.org/won` without quotes)
  
  to your run configuration.
  
3. Run your configuration

If you get a message indicating your keysize is restricted on startup (`JCE unlimited strength encryption policy is not enabled, WoN applications will not work. Please consult the setup guide.`), refer to [Enabling Unlimited Strength Jurisdiction Policy](https://github.com/open-eid/cdoc4j/wiki/Enabling-Unlimited-Strength-Jurisdiction-Policy) to increase the allowed key size.

### In Docker
- with [Dockerfile](https://github.com/researchstudio-sat/won-debugbot/blob/master/Dockerfile) `docker command to run or build idk yadayadayada`
- from [Docker Hub](https://hub.docker.com/r/webofneeds/won-debugbot) `docker pull blablabla`

Now [create an debug-atom](https://hackathon.matchat.org/owner/#!/create) to see the bot in action.
*to create a debug atom you have to click "Turn on Debugmode" in the Footer*


EXTRACTED FROM won-bot README.md Changes pending

Make sure this location contains the relevant property files, and you have specified the values of the properties relevant for the system being tested, i.e.:

- in [node-uri-source.properties](../conf/node-uri-source.properties)
  - won.node.uris - specify values of nodes being tested - the bot will react to atoms published on those nodes
- in [owner.properties](../conf/owner.properties)
  - specify default node data (node.default.host/scheme/port) - the bot will create its own atoms on that node
  - make sure both a path to keystore and truststore (keystore/truststore.location) and their password (keystore/truststore.password) is specified. For additional details on the necessary keys and certificates, refer to the Web of Needs [installation notes](https://github.com/researchstudio-sat/webofneeds/blob/master/documentation/installation-cryptographic-keys-and-certificates.md).

> **NOTE:** Use a separate keystore (and key pair) for your bot, especially if you are running another owner application locally - this will result in the node not delivering messages correctly because the queues used for delivery are defined based on certificates. If multiple applications from the same source share a certificate, there will be errors.

> **NOTE:** For the same reason as above, do not run several bot applications at the same time, - stop one before running another or separate their configurations.

> **NOTE:** Keystore and truststore paths have to be specified, but the files themselves do not have to exist initially, they will be created automatically. If you registered to a node using a different certificate before, the keystore and truststore need to be deleted to be able to register correctly again.
