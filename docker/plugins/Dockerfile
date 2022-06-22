# syntax=docker/dockerfile:1.4

FROM azul/zulu-openjdk:11.0.5

ENV CORDA_BASE="/opt/override"
ENV LOG4J_CONFIG_FILE="log4j2-console.xml"
ENV ENABLE_LOG4J2_DEBUG="false"
ENV CONSOLE_LOG_LEVEL="INFO"

ADD --chown=corda:corda /tools/plugins/build/tmp/publishOSGiImage/containerization ${CORDA_BASE}/plugins
ADD --chown=corda:corda /docker/plugins/cli.jar ${CORDA_BASE}/cli.jar

#ENTRYPOINT ["bin/sh","-c","exec java -Dlog4j.configurationFile=log4j2-console.xml -Dpf4j.pluginsDir=/opt/override/plugins -jar /opt/override/cli.jar \"${@}\"","--"]
ENTRYPOINT ["/bin/sh","-c","exec java -Dlog4j.configurationFile=${LOG4J_CONFIG_FILE} -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j.configurationFile=${LOG4J_CONFIG_FILE} -jar /opt/override/cli.jar $@","$@"]