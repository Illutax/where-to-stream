#############################################################
# Global args
ARG MVN_BASE_IMAGE=maven:3-amazoncorretto-25-alpine
ARG JDK_BASE_IMAGE=amazoncorretto:25-alpine
ARG DOCKER_IMAGE_TAG

#############################################################
# pre-fetch dependencies
FROM $MVN_BASE_IMAGE AS dependencies
COPY pom.xml .
RUN mvn -B -e org.apache.maven.plugins:maven-dependency-plugin:go-offline

#############################################################
# build jar
FROM $MVN_BASE_IMAGE AS builder
ARG DOCKER_IMAGE_TAG
ENV DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG

# For Debugging:
#RUN apt update &&  \
#    apt upgrade -y &&  \
#    apt install tree -y

WORKDIR /opt/app
COPY --from=dependencies pom.xml .
COPY --from=dependencies /root/.m2 /root/.m2
COPY src/ ./src/
RUN echo "$DOCKER_IMAGE_TAG" | mvn versions:set -DnewVersion= -DgenerateBackupPoms=false
RUN mvn package -DskipTests

# For Debugging:
#ENTRYPOINT ["ls","-la", "target"]

#############################################################
# run
FROM $JDK_BASE_IMAGE
LABEL authors="VDobler"
LABEL app="w2s"

WORKDIR /opt/app
COPY --from=builder /opt/app/target/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
