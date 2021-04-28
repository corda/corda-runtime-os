## Avro Code Source Files

All Arvo definitions should go under `src/main/resources/avro`.  While technically
not necessary, it would also be good to put them under the same directory structure as 
the package (or `namespace` in Avro-speak) that the record will belong.

For details on what records you can define using Avro see:

- [Getting Started](https://avro.apache.org/docs/current/gettingstartedjava.html)
- [Specification](https://avro.apache.org/docs/current/spec.html)

## Generating Avro Code

In order to create any new classes for Avro messaging you will need to invoke
the gradle task `generateAvro`.  This will (re)create the classes in `src/main/java`.

Additionally, you will want to run `generateOSGiPackageInfo`, which will ensure that all code
generated Avro classes are correctly exposed to OSGi.

Both tasks will be run when you `assemble`/`build` your project.

You will then need to commit any new files or changes as we version control the
generated classes.  Be aware that there are three types of version controlled classes:

- The Avro files which define the message types (`.avsc`)
- The generated class files that Avro will create (`.java`)
- The resource file which allows the generated class files to be loaded (`generated-avro-message-classes.txt`)