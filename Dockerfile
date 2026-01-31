ARG MVN_BASE_IMAGE=maven:3.9.12-eclipse-temurin-25
ARG JDK_BASE_IMAGE=openjdk:25-ea-21-jdk-slim

# pre-fetch dependencies
FROM $MVN_BASE_IMAGE AS dependencies
COPY pom.xml .
RUN mvn -B -e org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

FROM $MVN_BASE_IMAGE AS builder
LABEL authors="VDobler"

# For Debugging:
#RUN apt update &&  \
#    apt upgrade -y &&  \
#    apt install tree -y

WORKDIR /opt/app
COPY --from=dependencies pom.xml .
COPY --from=dependencies /root/.m2 /root/.m2
COPY src/ ./src/

RUN mvn clean package -DskipTests

# For Debugging:
#ENTRYPOINT ["ls","-la", "target"]

FROM $JDK_BASE_IMAGE

WORKDIR /opt/app
COPY --from=builder /opt/app/target/*.jar /app.jar

EXPOSE 8080
ARG FOO

ENTRYPOINT ["java", "-jar", "/app.jar"]
