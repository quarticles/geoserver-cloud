ARG REPOSITORY=geoservercloud
ARG TAG=latest

FROM $REPOSITORY/gs-cloud-base-jre:$TAG AS builder

ARG JAR_FILE=target/gs-cloud-*-bin.jar

COPY ${JAR_FILE} application.jar

RUN java -Djarmode=layertools -jar application.jar extract

##########
FROM $REPOSITORY/gs-cloud-base-geoserver-image:$TAG

# WORKDIR already set to /opt/app/bin

COPY --from=builder dependencies/ ./
COPY --from=builder snapshot-dependencies/ ./
COPY --from=builder spring-boot-loader/ ./
#see https://github.com/moby/moby/issues/37965
RUN true
COPY --from=builder application/ ./

# Execute the CDS training run (best-effort - failures won't break the build)
RUN mkdir /tmp/tmpdatadir && \
    GEOSERVER_DATA_DIR=/tmp/tmpdatadir java \
    -XX:ArchiveClassesAtExit=application.jsa \
    -Dspring.context.exit=onRefreshed \
    -Dspring.profiles.active=standalone,datadir,offline \
    -Dserver.port=0 -Dmanagement.server.port=0 \
    org.springframework.boot.loader.JarLauncher || \
    echo "CDS training run failed - continuing without CDS optimization"
RUN rm -rf /tmp/*

# Only use CDS archive if it was successfully created
ENV JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} $(test -f application.jsa && echo '-XX:SharedArchiveFile=application.jsa' || echo '')"
