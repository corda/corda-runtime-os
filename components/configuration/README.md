Typically, this is the sequence for initialisation of configuration:

1. A worker starts as a top level JVM command line program, via OSGi, and gets its command line arguments. 
   This might be a single-purpose worker such as the RPC worker, or, in the non-production local development mode  
   might be the combined worker.
2. `WorkerHelpers.getBootstrapConfig` is called to put together a validated smart config object. This
   bootstrap configuration will typically be incomplete since it will only contain keys specified on the command line,
   rather than all keys specified in the JSON schema of the configuration in the `corda-api.git/data/config-schema`,
   but what is in it is guaranteed to match the schema, or else this step will fail.
3. The bootstrap config is passed to the start method of a Corda component, e.g. a processor (or in the
   case of the combined worker, to all the processors). The processor will have the
   `ConfigurationReadService` dependency injected into its constructor.
4. The processor will then post a `BootConfigEvent` to their lifecycle coordinator, with the bootstrap
   configuration embedded in the event. The lifecycle coordinator event handler will call back to the processor
   to handle lifecycle events, such as the `BootConfigEvent` the processor just posted. 
   The main point of the lifecycle coordinator is to allow hooks to get run so that, for instance,
   components that depend on the processor can react appropriately to their dependency becoming
   configured. The lifecycle coordinator stores events in a queue stored in memory, 
   in its embedded `LifecycleStateManager`.
5. The `BootConfigEvent` handle of the processor will call `ConfigurationReadServiceImpl.bootstrapConfig` method to 
   store the boot configuration. 
6. `ConfigurationReadServiceImpl.bootstrapConfig`will post a `BootstrapConfigProvided` event to the 
   lifecycle coordinator of the `ConfigurationReadService`, which is a different lifecycle coordinator to that of 
   the processor.
7. The config read service `BootstrapConfigProvided` event handler will check that it hasn't
   already been set up with a non-identical boot configuration, and will store the boot configuration
   in memory as both the `bootConfiguration` instance variable and the BOOT_CONFIG item in the
   configuration instance variable.
8. As a side effect of receiving the boot configuration, we post the lifecycle coordinator `SetupSubscription` 
   from the [handleBoostrapConfig] method. This is also called if a [StartEvent] is received after the
   `bootstrapConfig` lifecycle event has been received.
9. When the `SetupSubscription` event is received then `ConfigReadServiceEventHandler.setupSubscription` is called.
   `ConfigReadServiceEventHandler` instances are always 1:1 associated with `ConfigReadServiceImpl`; the event
   handling is pulled out to a separate class simply to keep classes smaller and more focussed.
10. `ConfigReadServiceEventHandler.setupSubscription` will throw if the `bootstrapConfiguration` instance variable 
    has not been set, which should be impossible.
11. `ConfigReadServiceEventHandler.setupSubscription` will throw if it has already run, causing the
    `subscription] instance variable to be set.
12. `ConfigReadServiceEventHandler.setupSubscription` will create a `ConfigProcessor` and store it in an 
     instance variable. `ConfigProcessor` is always 1:1 `ConfigReadServiceEventHandler` and only used by that class,
     so is also 1:1 with the `ConfigurationReadServiceImpl`. The purpose of the `ConfigProcessor` is to receive
     snapshots with all the configuration and configuration updates to different parts of the configuration
13. `ConfigReadServiceEventHandler.setupSubscription` will also create a compacted topic Kafka subscription, 
     hooked up to the `ConfigProcessor` instance. The compacted topic forms a map of strings to `Configuration`
     objects. For example, with the combined worker, we would see 8 keys, such as "corda.membership" and
     "corda.cryptoLibrary". The values in the map are Avro `Configuration` objects, which have:
        - a string field `value` which is a JSON representation, with defaults applied
        - a string field `source` which is a JSON representation, without defaults applied
        - an int field `version` (starts at 0)
        - a field `schemaVersion` (typically 1.0 at the moment)
    Initially the map will be empty.
14. Popping back out to the processor eventHandler `BootConfigEvent` logic, it may now be appropriate to 
    set up more of the processor
15. Whenever a component changes its lifecycle status, `Registration.updateCoordinatorStatus` runs, and will generate a
    `RegistrationStatusChangeEvent` lifecycle event.  
16. The DB processor has a number of `Reconciler` instances, including a `ConfigReconciler`. The function of
    the `ConfigReconciler` is to keep in sync what's in the database with what's in Kafka. On a fresh system,
    the configuration will already have been written to the database by this point (TODO: explain how), so
    the `ConfigReconciler` will write a number of messages to the `CONFIG_TOPIC`. The `ReconcilerEventHandler` will
   receive a `RegistrationStatusChangeEvent` lifecycle event and call `ReconcilerEventHandler.reconcileAndScheduleNext`
17. `ReconcilerEventHandler.reconcileAndScheduleNext` will do a timed call to `ReconcilerEventHandler.reconcile`
18. `ReconcilerEventHandler.reconcile` will get all the Kafka records and all the database records, and will call
    `ConfigPublishServiceImpl.put` on each new record.
19. `ConfigPublishServiceImpl.put` will call `ConfigPublishServiceImpl.validateConfigAndApplyDefaults` to turn what
    it got, which may be an empty string, into a fully populated record. This is done by making a `SmartConfig` record,
    which could be empty, then calling `ConfigurationValidatorImpl.validate` with that sparse config object.
20. `ConfigurationValidatorImpl.validate` obtains the schema for the key in question from `SchemaProviderImpl`,
    then calling `ConfigurationValidatorImpl.validateConfigAndGetJSONNode`. This will throw exceptions if there
    are mismatches and will set defaults in the config.
21. TODO: describe typical ways config is accessed, via a `SmartConfig` object.
