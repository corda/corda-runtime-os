## JSON Schemas and Validation
Configuration stored on the kafka config topic and DB worker must conform to a JSON schema. These schemas are defined within the corda-api project under the [config schema module.](https://github.com/corda/corda-api/tree/release/os/5.0/data/config-schema) Each config key section will have its own JSON schema. The JSON schemas themselves must conform to the JSON specification defined in the `$schema` entry at the top of the JSON schema files. 

Any configuration submitted to the RPC worker via the `/config` endpoint is validated by the RPC worker using these schemas. If the validation is successful the config is passed to DB worker where it will again be validated before being stored in the DB. [Further reading here.](https://github.com/corda/corda-runtime-os/wiki/Cluster-configuration)

Secrets must be defined in the JSON schema as a string at the path of the secret. If the config stored under the secret path is a complex object the schema entry must be defined as a string regardless. [Further reading here.](https://github.com/corda/corda-runtime-os/wiki/Secrets-configuration)

Schemas are versioned based on the directory structure using a major and minor version. e.g `net\corda\schema\configuration\messaging\1.0\corda.messaging.json`. 

Additional info can be found in the README of the corda-api config module:
https://github.com/corda/corda-api/tree/release/os/5.0/data/config-schema#readme


### Defaults
Default values can be defined in the JSON schema files. Defaults are applied when the DB worker writes config to the kafka config topic. If a value is explicitly set to null in the config no default will be applied. If the field is missing from a config request, the default logic will add that field to the schema if there is a default value defined. 

The default logic is implemented via a JSON walker which traverses the node applying defaults at each level. If a level is completely absent from the configuration no defaulting will be able to be applied to the sub nodes defined in the schema. 

For example if the path `a.b.c.d` has a default value of `10` but the config passed into the system only had the paths `a.b` present then no nodes will be added to set `d`. To avoid this happening we simply need to define default values for complex nodes as `{}`. This will then insert a node at this level and then the walker can insert a default value for `d`.

e.g
```
"properties": {
    "a": {
      "type": "object",
      "default": {},
      "properties": {
        "b": {
          "default": {},
          "type": "object",
          "properties": {
            "c": {
              "type": "object",
              "default": {},
              "properties": {
                "d": {
                  "description": "This value will get defaulted correctly :)",
                  "type": "integer",
                  "minimum": 1,
                  "default": 10
                }
            }
          }
        }
      }
    }
```

#### Required property and Defaults
If a field is marked as required and also has a default value set in the schema, but is left absent from the config being validated an error will be returned. The default will not be applied before validation. Therefore there is little value in defining a property as required and also defining a default value.