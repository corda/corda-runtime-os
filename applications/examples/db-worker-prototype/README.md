# Database worker prototype

This is a crude prototype of the DB Worker application which:
- Will be provided with connectivity parameters to Kafka broker and Postgres DB; 
- Upon start-up run Liquibase update as necessary to create RBAC schema;
- Launch a component (`PermissionStorageWriterService`) that listens on the user creation Kafka topic.

### Building the Docker image
The Gradle task `publishOSGiImage` publishes a Docker Image which can be run locally.
```
gradlew :applications:examples:db-worker-prototype:clean :applications:examples:db-worker-prototype:publishOSGiImage
```