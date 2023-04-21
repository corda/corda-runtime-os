# Config Schema

This module contains the schema which represents user-facing configuration properties.

There are three major components included in this module:

- Configuration key constants defining the top-level configuration keys (i.e. the major sections of config).
- JSON schema files defining the configuration schema.
- The `SchemaProvider`, which gives access to these files to code both under OSGi and without OSGi present.

These are discussed below.

## Configuration Key Constants

The configuration keys are top-level configuration sections. Configuration in the system is provided to the workers as
messages on the bus, where the key is one of these top-level keys and the value is the current configuration under that
section. This is also the structure of the stored configuration on the database. These top-level keys are provided in
the `ConfigKeys` object as string constants. Note that configuration can only be changed one key at a time.

The schema version is also provided along with the configuration data. This is the version of the schema for that section
that the configuration should conform to. This is primarily important for validation, but it can also be relevant for
performing data upgrades in the face of data provided under an old schema version. Note that full details of 
configuration version changes are not yet ironed out.

Other configuration constants are also provided. These are paths to the relevant configuration items under the top-level
key. They are primarily used to access configuration properties in the code. Each section should have its own object to
define these.

## JSON Schema Files

The JSON schema files in `main/resources` represent the schema that configuration data pushed into the system must
conform to. The schema is used to declare what properties exist, which are required, potentially restrict the allowed
values and to document the purpose and defaults of the configuration property.

More details on the JSON schema specification and how to write JSON schema files can be found [here](https://json-schema.org/).
[Understanding JSON schema](https://json-schema.org/understanding-json-schema/) is a good resource for learning the
specification.

We are using JSON schema draft 7, which gives a balance between available features and library support. Upgrading this
is non-trivial, so do not make changes to the draft version without checking first.

Schema files must be placed in a directory structure like below in resources:

`net/corda/schema/configuration/<key>/<version>/corda.<key>.json`

Where \<key> is the top-level section (e.g. messaging, flow, db) and \<version> is the schema version.

Files that the main schema refers to in $ref fields should be placed in the same directory.

### JSON paths and $ref

When writing JSON schema the most likely complication is around the $ref property. This is used to factor bits of schema
out into their own files, which can help for clarity and reuse. The value placed in a $ref field is a URI representing
where the schema file to insert at that point can be found.

This can lead to complications when using libraries to parse the schema. Our schema is using a dummy URL (https://corda.r3.com)
to indicate where to find the schema files. Performing a lookup here clearly won't work, so libraries need to be coerced
to look elsewhere when resolving our schema files. For the validation library used in the implementation (networknt),
this can be performed quite easily.

### Versioning

The schema is currently versioned using a `major.minor` version scheme. Each section is currently versioned separately.
Changes to schema files should aim to be backwards compatible (after the first release), which means that historic data
under old schema versions should validate against the new schema. If an update meets this criteria, the minor number
should be incremented. If it does not, the major number should be incremented instead.

On major schema updates, it may be required to provide some data upgrade code to handle migrating data from the old
version to the new version. This will need to be performed separately from the configuration write/read path.

## SchemaProvider

The `SchemaProvider` is a lookup mechanism for retrieving schema files by key and version. It relies on the conventions
outlined above to retrieve these files correctly. This should work under OSGi and non-OSGi scenarios.

The library provides an `InputStream` as output as many validation libraries expect to get these from their URI lookups
when trying to locate schema files. It is the responsibility of the caller to close this.

