# Dockerfile for won-debugbot
FROM openjdk:8u121-jdk
Run echo "TEST"

RUN -it --rm --name wen-debugbot -v "$(pwd)":/usr/src/debugbot -w /usr/src/debugbot maven:3.3-jdk-8 mvn clean install
