## Generating Avro Code

In order to create any new classes for Avro messaging you will need to invoke
the gradle task `generateAvro`.  This will (re)create the classes in `src/main/java`.

You will then need to commit any new files or changes as we version control the
generated classes.