# Configuration Management Process Flow

## Scope

To document the process for updating the configuration of a Corda cluster.

## Overview

The following diagram shows the end-to-end process for updating the configuration of a Corda cluster.

![Configuration management process flow diagram](./images/configuration_management_process_flow.jpeg)

The golden path has seven key steps:

1. An HTTP client sends an HTTP request to update the cluster configuration to the RPC worker
2. The RPC worker writes a configuration management request to the configuration management topic on the Kafka bus
3. The DB worker reads the configuration management request from the configuration management topic on the Kafka bus
4. The DB worker updates the configuration tables in the cluster database
5. The DB worker writes the updated configuration to the configuration topic on the Kafka bus
6. The RPC worker notifies the HTTP client of the success of their request
7. Workers can read the updated configuration from the configuration topic on the Kafka bus using the configuration read
   service

Note that there are two separate Kafka topics involved in this process:

1. The configuration topic, which holds the current state of the cluster configuration
2. The configuration management topic, which holds _requests_ to modify the current state of the cluster configuration

For simplicity, the diagram skips over the configuration management response that the DB worker writes to the
configuration management topic on the Kafka bus after step (5), which in turn allows the RPC worker to send a response
to the HTTP client in step (6).

The remainder of this document takes a closer look at various parts of the above process.

## HTTP requests for configuration updates

The RPC worker exposes an HTTP interface for managing cluster configuration. The endpoints for this interface are
defined by the `ConfigRPCOps` interface, as implemented by the `ConfigRPCOpsImpl` interface. The `HttpRpcGateway`
component discovers this implementation class at worker start-up and automatically serves its endpoints.

There is a single endpoint for configuration management:

`/api/v1/config/update`

Requests to this endpoint are expected to take the form of POST requests with the following body:

```
{
    "request": {
        "section": "configSection",
        "config": "key1=val1\nkey2=val2",
        "schemaVersion": 1,
        "version": -1
    }
}
```

Where we have:

* `section`: The section of the configuration to be updated
* `config` The updated configuration in JSON or HOCON format
* `schemaVersion` The schema version of the configuration
* `version` The version number used for optimistic locking. The request will fail if this version does not match the
  version stored in the database for the corresponding section, or -1 if this is a new section for which no
  configuration has yet been stored

These requests are automatically mapped to `HTTPUpdateConfigRequest` objects for handling by `ConfigRPCOpsImpl`.

Successful requests will receive a response with a success code (_2xx_) that contains the updated configuration in JSON
format, e.g.:

```
{
    "section": "configSection",
    "config": "key1=val1\nkey2=val2",
    "schemaVersion": 1,
    "version": 0
}
```

While unsuccessful requests are indicated by an error code (_5xx_).

These responses are automatically mapped from `HTTPUpdateConfigResponse` objects.

## Publication of configuration update requests by the RPC worker

`ConfigRPCOpsImpl` holds a reference to a running
`RPCSender<ConfigurationManagementRequest, ConfigurationManagementResponse>`. For each incoming HTTP configuration
update request to the RPC worker, the connection is held open and the RPC sender is used to publish a message to
the `config.management.request` Kafka topic. This message uses the `ConfigurationManagementRequest` Avro schema:

```
{
  "type": "record",
  "name": "ConfigurationManagementRequest",
  "namespace": "net.corda.data.config",
  "fields": [
    {
      "name": "section",
      "type": "string",
      "doc": "Section of the configuration to update."
    },
    {
      "name": "config",
      "type": "string",
      "doc": "Updated configuration in JSON or HOCON format."
    },
    {
      "name": "schemaVersion",
      "type": "int",
      "doc": "Schema version of the updated configuration."
    },
    {
      "name": "updateActor",
      "type": "string",
      "doc": "ID of RPC user that requested the configuration update."
    },
    {
      "name": "version",
      "type": "int",
      "doc": "Version of the configuration for optimistic locking."
    }
  ]
}
```

The RPC worker than awaits a response on the Kafka response topic. This message uses the
`ConfigurationManagementResponse` Avro schema:

```
{
  "type": "record",
  "name": "ConfigurationManagementResponse",
  "namespace": "net.corda.data.config",
  "fields": [
    {
      "name": "success",
      "type": "boolean",
      "doc": "Whether the request was successful."
    },
    {
      "name": "exception",
      "type": [
        "null",
        "net.corda.data.ExceptionEnvelope"
      ],
      "doc": "The cause of failure if the request was unsuccessful."
    },
    {
      "name": "section",
      "type": "string",
      "doc": "The configuration section for which an update was requested."
    },
    {
      "name": "config",
      "type": "string",
      "doc": "The current configuration in JSON format for the given section."
    },
    {
      "name": "schemaVersion",
      "type": "int",
      "doc": "The current configuration's schema version for the given section."
    },
    {
      "name": "version",
      "type": "int",
      "doc": "The current configuration's optimistic-locking version for the given section."
    }
  ]
}
```

If the `success` field is `true`, the configuration update request was successful, and a success HTTP response is sent
to the HTTP client. Otherwise, a failure HTTP response is sent, based on the error type and error message in the
`exception` field.

The HTTP connection is then closed.

## Persistence of configuration updates by the DB worker

The DB worker uses two tables in the cluster database to manage configuration, `config` and `configAudit`. These tables
are created using the following Liquibase scripts:

```
<createTable tableName="config" schemaName="${schema.name}">
    <column name="section" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
    <column name="config" type="TEXT">
        <constraints nullable="false"/>
    </column>
    <column name="schema_version" type="INT">
        <constraints nullable="false"/>
    </column>
    <column name="update_ts" type="DATETIME">
        <constraints nullable="false"/>
    </column>
    <column name="update_actor" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
    <column name="version" type="INT">
        <constraints nullable="false"/>
    </column>
</createTable>
<addPrimaryKey columnNames="section" constraintName="config_pk" tableName="config"
               schemaName="${schema.name}"/>

...

<createTable tableName="config_audit" schemaName="${schema.name}">
    <column name="change_number" type="SERIAL">
        <constraints nullable="false"/>
    </column>
    <column name="section" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
    <column name="config" type="TEXT">
        <constraints nullable="false"/>
    </column>
    <column name="config_version" type="INT">
        <constraints nullable="false"/>
    </column>
    <column name="update_ts" type="DATETIME">
        <constraints nullable="false"/>
    </column>
    <column name="update_actor" type="VARCHAR(255)">
        <constraints nullable="false"/>
    </column>
</createTable>
<addPrimaryKey columnNames="change_number" constraintName="config_audit_pk" tableName="config_audit"
               schemaName="${schema.name}"/>
<createSequence sequenceName="config_audit_id_seq"/>
```

The DB worker listens for incoming configuration management requests using an
`RPCSubscription<ConfigurationManagementRequest, ConfigurationManagementResponse>` that consumes
`ConfigurationManagementRequest` messages from the `config.management.request` topic. These messages are handled by the
`ConfigWriterProcessor`.

For each message, the DB worker creates a corresponding `ConfigEntity` and `ConfigAuditEntity`, and attempts to persist
them to the database. The only non-technical reason the update might fail is
[optimistic locking](https://docs.jboss.org/hibernate/orm/4.0/devguide/en-US/html/ch05.html#d0e2225). The `config`
table contains a `version` column, and configuration update requests for which the `version` field does not match the
current version in the database are rejected.

## Publication of configuration updates by the DB worker

If the database tables are updated successfully, the DB worker then publishes a message to the `config` topic. This
message follows the `Configuration` Avro schema:

```
{
  "type": "record",
  "name": "Configuration",
  "namespace": "net.corda.data.config",
  "fields": [
    {
      "name": "value",
      "type": "string"
    },
    {
      "name": "version",
      "type": "string"
    }
  ]
}
```

This message can then be consumed off the topic by other workers via the `ConfigurationReadService` component to learn
the current state of the cluster configuration.

If the persistence to the database and the publication to the `config` topic succeed, the DB worker responds to the RPC
worker by publishing a `ConfigurationManagementResponse` message to the `config.management.request` response topic with
the `success` field set to `true`. Otherwise, it publishes a message with the `success` field set to `false`, with
the `exception` field documenting the cause of the failure.