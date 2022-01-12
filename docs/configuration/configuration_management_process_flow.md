# Configuration Management Process Flow

## Scope

To document the process for updating the configuration of a Corda cluster.

## Overview

The following diagram shows the end-to-end process for updating the configuration of a Corda 
cluster (identified simply as "the cluster" hereafter).

![Configuration management process flow diagram](./images/configuration_management_process_flow.jpeg)

The golden path has seven key steps:

1. An HTTP client sends an HTTP request to update the cluster configuration to the RPC worker
2. The RPC worker writes a configuration management request to the configuration management topic 
   on the Kafka bus
3. The DB worker reads the configuration management request from the configuration management 
   topic on the Kafka bus
4. The DB worker updates the configuration tables in the cluster database
5. The DB worker writes the updated configuration to the configuration topic on the Kafka bus
6. The RPC worker notifies the HTTP client of the success of their request
7. Workers can read the updated configuration from the configuration topic on the Kafka bus using 
   the configuration read service

Note that there are two separate Kafka topics involved in this process:

1. The configuration topic, which holds the current state of the cluster configuration
2. The configuration management topic, which holds _requests_ to modify the current state of the 
   cluster configuration

For simplicity, the diagram skips over the configuration management response that the DB worker 
writes to the configuration management topic on the Kafka bus after step (5), which in turn allows 
the RPC worker to send a response to the HTTP client in step (6).

The remainder of this document takes a closer look at various parts of the above process.

## HTTP protocol for requesting a configuration update

The RPC worker exposes the following endpoint:

   `/api/v1/config/update`
   
Requests are expected to take the form of POST requests with the following body:

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
* `version` The version number used for optimistic locking. The request will fail if this version 
  does not match the version stored in the database for the corresponding section, or -1 if this 
  is a new section for which no configuration has yet been stored

Successful requests will receive a response with a success code (_2xx_) that contains the updated 
configuration in JSON format, e.g.:

   ```
   {
      "config": "key1=val1\nkey2=val2"
   }
   ```

While unsuccessful requests are indicated by an error code (_5xx_).

## RPC protocol for interaction between the RPC worker and the DB worker

TODO