# Dockerfile for won-debugbot
FROM maven
Run echo "Start maven"

COPY pom.xml /tmp/
COPY src /tmp/src/
WORKDIR /tmp/
RUN mvn packag
