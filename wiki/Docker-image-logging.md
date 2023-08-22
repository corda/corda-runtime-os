# Building a specific Docker image

To build a specific docker image, run this command:

`./gradlew :applications:workers:release:db-worker:publishOSGiImage`

# Running a specific Docker image

To run a specific docker image, run this command:

`docker run corda-os-docker-dev.software.r3.com/corda-os-db-worker:latest-local`

The above command logs to the console. Alternatively, to log to `/logs/app.log`, specify the alternate LOG4J Config File using `LOG4J_CONFIG_FILE`. For example:

`docker run -e LOG4J_CONFIG_FILE=log4j2.xml corda-os-docker-dev.software.r3.com/corda-os-db-worker:latest-local`
