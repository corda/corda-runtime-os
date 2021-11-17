# Config Schema

This module contains the schema which represents configurations.

The following characteristics are important when considering the configuration:

- The configuration will not actually be in files but will be stored in a JSON/HOCON like manner and made available via
the configuration service.
- When updating configuration we expect that only one key will be changed at a time.
- A configuration "key" will be the top level value of that configuration (see examples in the sections below).
- Long term plans expect a service to validate the configuration in addition to simply read/write operations.

## Virtual Node Configuration

There will be several sub-sections of configuration, each specific to a unique area.  However, the root configuration
will always be `corda`.

Each key, then, for the configuration service will be `corda.x` where `x` is the subsection configuration.

For example, for flow configuration the configuration key would be `corda.flow`.

```
corda {
    flow {
        // Configuration goes here
    }
}
```

Updating the flow config would require updating the `SmartConfig` and publishing it with the key `corda.flow`.

Any subscriptions would then receive an `onNewConfiguration` update with the key as `corda.flow` and the published 
changes in a map of `corda.flow` to the config.


## Cordapp Configuration

Similarly to the virtual node configuration there will be cordapp configuration.  For these cases 
the root configuration will be `cordapp`.
