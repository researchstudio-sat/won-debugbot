# Dockerfile for won-debugbot
FROM openjdk:8u121-jdk
Run echo "Start maven"

#RUN --rm --name won-debugbot -v "$(pwd)":/usr/src/debugbot -w /usr/src/debugbot maven:3.3-jdk-8 mvn clean install
COPY pom.xml /tmp/pom.xml
RUN mvn -B -f /tmp/pom.xml -s /usr/share/maven/ref/settings-docker.xml dependency:resolve