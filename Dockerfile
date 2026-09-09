#############################################################
# Global args
ARG MVN_BASE_IMAGE=maven:3-amazoncorretto-25-alpine
ARG JDK_BASE_IMAGE=amazoncorretto:25-alpine
# Pin the Node.js major used to build the Angular client, independent of the Alpine repo state.
# This is one of three places naming that major; ToolchainVersionsAgreeTest fails the build if they
# drift apart. To move to the next Node LTS, change all three (see ADR-0021).
ARG NODE_BASE_IMAGE=node:24-alpine
ARG DOCKER_IMAGE_TAG

#############################################################
# pinned Node.js toolchain (musl/Alpine, same libc as the corretto-alpine images)
FROM $NODE_BASE_IMAGE AS nodejs

#############################################################
# Build toolchain: Maven plus the pinned Node, and nothing project-specific.
#
# Every stage that compiles anything derives from here, so the toolchain is described exactly once.
# That matters beyond tidiness: upgrade-spring-boot.sh used to verify a Spring Boot bump with the
# HOST's `mvn clean package`, while the shipped artifact was built from this file. The two could be
# green and red independently -- a Node upgrade on the host broke the nightly check even though the
# image would still have built. Both now go through the `verify`/`builder` stages below.
#
# Node is copied from the pinned node:24-alpine stage rather than `apk add nodejs`, so the version
# does not follow the Alpine repo state. libstdc++/libgcc are node's musl runtime deps.
# registry.npmjs.org must be reachable during the build (set NPM_CONFIG_REGISTRY for a mirror).
FROM $MVN_BASE_IMAGE AS toolchain
WORKDIR /opt/app
RUN apk add --no-cache libstdc++ libgcc
COPY --from=nodejs /usr/local/bin/node /usr/local/bin/node
COPY --from=nodejs /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
 && node --version && npm --version

#############################################################
# pre-fetch dependencies (cached layer: only invalidated by pom.xml)
FROM toolchain AS dependencies
COPY pom.xml .
RUN mvn -B -e org.apache.maven.plugins:maven-dependency-plugin:go-offline

#############################################################
# toolchain + dependencies + sources; the one place the project is copied in
FROM toolchain AS sources
COPY --from=dependencies /opt/app/pom.xml .
COPY --from=dependencies /root/.m2 /root/.m2
COPY src/ ./src/

#############################################################
# the artifact that ships -- built WITH tests.
#
# `update-and-restart.sh` runs a plain `docker build .`, which builds the runtime stage below and
# therefore this one. Skipping tests here would mean deploying code that nothing verified: the
# nightly check in upgrade-spring-boot.sh only runs when Spring Boot itself released something, so
# every ordinary commit would reach production untested.
#
#
# `-Dtest.excluded.groups=testcontainers` is the one exception to "built WITH tests": a build stage
# has no Docker socket, so the Testcontainers (MariaDB) tests cannot start a database here and would
# fail for want of a runtime rather than for want of correctness. They are the default everywhere a
# runtime exists -- a developer's `mvn verify` and CI -- so the gap is one build, not the habit.
#
# The version is stamped before the build rather than after, so the tests run against the same
# coordinates that ship.
FROM sources AS builder
ARG DOCKER_IMAGE_TAG
ENV DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG
RUN echo "$DOCKER_IMAGE_TAG" | mvn versions:set -DnewVersion= -DgenerateBackupPoms=false
RUN mvn -B package -Dtest.excluded.groups=testcontainers

#############################################################
# the same toolchain and the same tests, without the version stamping -- built by
# upgrade-spring-boot.sh to check a dependency bump before it is committed:
#   docker build . --target verify
# Deliberately a sibling of `builder` rather than its parent: the deploy build must not drag an
# extra frontend compilation along, and this target needs no DOCKER_IMAGE_TAG. Both share the
# cached `sources` stage, so the duplication is in the file, not in the work.
FROM sources AS verify
RUN mvn -B clean package -Dtest.excluded.groups=testcontainers

#############################################################
# run
FROM $JDK_BASE_IMAGE
LABEL authors="VDobler"
LABEL app="w2s"

WORKDIR /opt/app
COPY --from=builder /opt/app/target/*.jar /app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
