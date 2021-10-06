ARG JDK_BASE="azul/zulu-openjdk:11"
FROM $JDK_BASE

RUN echo $JDK_BASE

RUN java --version

RUN useradd -ms /bin/bash corda

WORKDIR /opt/

# HTTP RPC Gateway main JAR
ARG JAR_NAME="corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar"
COPY build/bin/$JAR_NAME .

# Kafka configuration file
ARG KAFKA_PROP="kafka.properties"
COPY $KAFKA_PROP .

RUN chown corda:root . -R

USER corda
## VOLUME ["/opt/nodes"]

EXPOSE 8888/tcp

ENTRYPOINT ["java", "-jar", "corda-http-rpc-gateway-5.0.0.0-SNAPSHOT.jar", "--instanceId", "1", "--kafka", "kafka.properties"]