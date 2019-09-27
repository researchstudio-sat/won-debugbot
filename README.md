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

## This Bot is available as a [Docker Container](https://hub.docker.com/r/webofneeds/won-debugbot)
