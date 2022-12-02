# syntax=docker/dockerfile:1.4

ARG BASE_IMAGE

FROM ${BASE_IMAGE}

ARG IMAGE_ENTRYPOINT="exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/override/crypto.jar"
ARG BUILD_PATH="/applications/workers/release/crypto-worker/build/tmp/publishOSGiImage/containerization/corda-crypto-worker-5.0.0.0-SNAPSHOT.jar"
ARG JDBC_PATH="/applications/workers/release/crypto-worker/build/tmp/publishOSGiImage/jdbc-driver"
ARG JAR_LOCATION="/opt/override/"
ARG JDBC_DRIVER_LOCATION="/opt/jdbc-driver/"

ENV IMAGE_ENTRYPOINT_ENV=${IMAGE_ENTRYPOINT}
ENV CORDA_BASE="/opt/override"
ENV CORDA_CLI_HOME_DIR="/opt/override/home"
ENV LOG4J_CONFIG_FILE="log4j2-console.xml"
ENV ENABLE_LOG4J2_DEBUG="false"
ENV CONSOLE_LOG_LEVEL="INFO"

ADD --chown=corda:corda ${BUILD_PATH} ${JAR_LOCATION}
ADD --chown=corda:corda ${JDBC_PATH} ${JDBC_DRIVER_LOCATION} 

ENTRYPOINT ["/bin/sh","-c","${IMAGE_ENTRYPOINT_ENV} $@","$@"]
