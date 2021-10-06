ARG JDK_BASE="azul/zulu-openjdk:11"
FROM $JDK_BASE

RUN echo $JDK_BASE

RUN java --version

RUN useradd -ms /bin/bash corda

WORKDIR /opt/

# HTTP RPC Gateway main JAR
ARG JAR_NAME="corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar"
ENV JAR_NAME_ENV=$JAR_NAME
COPY build/bin/$JAR_NAME .

# Kafka configuration file
ARG KAFKA_PROP="kafka.properties"
ENV KAFKA_PROP_ENV=$KAFKA_PROP
COPY $KAFKA_PROP .

RUN chown corda:root . -R

USER corda

EXPOSE 8888/tcp

ENTRYPOINT java -jar $JAR_NAME_ENV --instanceId 1 --kafka $KAFKA_PROP_ENV