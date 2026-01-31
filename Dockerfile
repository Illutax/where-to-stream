# pre-fetch dependencies
FROM maven:3.9.12-eclipse-temurin-25 AS DEPENDENCIES
COPY pom.xml .
RUN mvn -B -e org.apache.maven.plugins:maven-dependency-plugin:3.1.2:go-offline

FROM maven:3.9.12-eclipse-temurin-25 as builder
LABEL authors="VDobler"

RUN apt update &&  \
    apt upgrade -y &&  \
    apt install tree -y

WORKDIR /opt/app
COPY --from=DEPENDENCIES pom.xml .
COPY --from=DEPENDENCIES /root/.m2 /root/.m2
COPY src/ ./src/

RUN mvn clean package -DskipTests

#ENTRYPOINT ["ls","-la", "target"]

FROM  openjdk:25-ea-21-jdk-slim

WORKDIR /opt/app
COPY --from=builder /opt/app/target/*.jar /app.jar

EXPOSE 8080
ARG FOO

ENTRYPOINT ["java", "-jar", "/app.jar"]
