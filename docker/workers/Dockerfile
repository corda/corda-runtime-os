# syntax=docker/dockerfile:1.4

FROM azul/zulu-openjdk:11.0.5

ARG workerType="combined"

ENV CORDA_BASE="/opt/corda"
ENV LOG4J_CONFIG_FILE=
ENV ENABLE_LOG4J2_DEBUG=
ENV CONSOLE_LOG_LEVEL="INFO"

ADD --chown=corda:corda /applications/workers/release/${workerType}-worker/build/bin/corda-${workerType}-worker-5.0.0.0-SNAPSHOT.jar /opt/corda/bin/corda-worker.jar

ENTRYPOINT ["bin/sh","-c","exec java -Dlog4j2.debug=false -Dlog4j.configurationFile=log4j2-console.xml -jar /opt/corda/bin/corda-worker.jar \"${@}\"","--"]