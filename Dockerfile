#############################################################
# Global args
ARG MVN_BASE_IMAGE=maven:3-amazoncorretto-25-alpine
ARG JDK_BASE_IMAGE=amazoncorretto:25-alpine
# Pin the Node.js used to build the Angular client to a fixed version (matches the range in
# src/main/frontend/package.json "engines" and .nvmrc), independent of the Alpine repo state.
ARG NODE_BASE_IMAGE=node:24-alpine
ARG DOCKER_IMAGE_TAG

#############################################################
# pinned Node.js toolchain (musl/Alpine, same libc as the corretto-alpine images)
FROM $NODE_BASE_IMAGE AS nodejs

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

# The build also compiles the Angular client (src/main/frontend) via npm, so the builder needs
# Node.js/npm. Instead of an unpinned `apk add nodejs`, copy the pinned Node from the node:24-alpine
# stage (deterministic version across machines). libstdc++/libgcc are node's musl runtime deps.
# registry.npmjs.org must be reachable during the image build (set NPM_CONFIG_REGISTRY to a mirror
# if it is not). Pass -Dskip.frontend=true to build the backend only.
RUN apk add --no-cache libstdc++ libgcc
COPY --from=nodejs /usr/local/bin/node /usr/local/bin/node
COPY --from=nodejs /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
 && node --version && npm --version

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
