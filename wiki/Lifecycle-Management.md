# Component Lifecycles

The 'Lifecycle' library is provided as a mechanism to allow components access to a small state machine, 
providing a single threaded response for event handling.  Such events can be (but are not limited to):

- `UP`/`DOWN`/`ERROR` status
- Configuration change notifications
- Any other dynamic events that a component might want to react to

By providing this simple state machine components can expect all incoming events to be provided from 
within a single thread.  This removes the need for some concurrency handling.

## Lifecycle vs Resource

In the library there are two distinct types which are provided: `Lifecycle` and `Resource`.  Each type
has different semantics and different expectations around usage.

### Lifecycle

The `Lifecycle` is primarily designed for long-lived objects, for example, system components (OSGi singletons).

These objects will likely be started and primarily run the lifetime of the application.  They should, however, be
capable of stopping and then starting again, if necessary.  It is not expected that they would ever be "closed" as
the only time a `Lifecycle` object should be "done" is at application exit.

For the most recent updates see the [Lifecycle API](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle/src/main/kotlin/net/corda/lifecycle/Lifecycle.kt)

```
/**
 * This interface defines a component it can [start] and [stop] and be used as a try-with-resource as
 *
 * ```kotlin
 * object: Lifecycle { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the component goes out of scope, [close] is automatically called, hence [stop].
 */
interface Lifecycle {
    /**
     * Override to define how the component starts.
     *
     * It should be safe to call start multiple times without side effects.
     */
    fun start()

    /**
     * Override to define how the component stops: close and release resources in this method.
     *
     * It should be safe to call stop multiple times without side effects.
     */
    fun stop()
}
```

### Resource

By contrast, a `Resource` is designed to be created, used, then closed. And, if necessary, created again.
An example of resources would be subscriptions, which need to be recreated when relevant configuration changes 
or when restarting a component.

Note that resources don't have `start`/`stop` semantics.  They are expected to be running upon creation and 
will continue running until `close` is called, at which point they cannot be started again.

For the most recent updates see the [Resource API](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle/src/main/kotlin/net/corda/lifecycle/Resource.kt)

```
/**
 * This interface defines a resource owned and controlled by a component.
 * It can [start] and [close] and be used as a try-with-resource as
 *
 * ```kotlin
 * object: Lifecycle { ... }.use { lifecycle -> ... }
 * ```
 *
 * When the resource goes out of scope, [close] is automatically called
 */
interface Resource : AutoCloseable {
    /**
     * Automatically called when this resource is out of try-with-resource scope.
     *
     * Further, it is not expected that a closed object should be restarted.
     *
     * See [AutoCloseable.close]
     */
    override fun close()
}
```

## Configuration management using lifecycles

Configuration management is one of the most common use cases of the lifecycle updates that a component
will use - specifically, being notified and adjusting for configuration changes.  For this reason the
`ConfigurationReadService` has been developed to unify configuration handling.

When registering your component for configuration prefer to use `registerComponentForUpdates` with your component
and the set of configuration keys that your component needs to use.  This API will ensure that you are given the
configuration you specifically want.  In addition, it is more robust than the previous version, which is now deprecated.

For more detail, follow the API for [ConfigurationReadService](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/components/configuration/configuration-read-service/src/main/kotlin/net/corda/configuration/read/ConfigurationReadService.kt).

## Using `DependentComponents.of` to specify dependent components

Additionally, your component may have a set of dependencies upon which it relies and also which can be started/stopped
as a single unit.  This is likely for specific dependencies which are injected and only expected to be used by your
component.

There is a helper utility provided for these classes called `DependentComponents`.  When specifying your dependencies
via this utility you can more simply register/start and stop all dependencies.

The preferable usage of this tool is to pass it to the `LifecycleCoordinator` upon construction.  The `LifecycleCoordinator` can then manage the component start/stop for you.  For example,

```kotlin
    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<FlowProcessorImpl>(dependentComponents, ::eventHandler)
```

For more detail, follow the API for [DependentComponents](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle/src/main/kotlin/net/corda/lifecycle/DependentComponents.kt).

Also see the [LifecycleCoordinatorFactory API](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle/src/main/kotlin/net/corda/lifecycle/LifecycleCoordinatorFactory.kt).

## Using Managed Resources to automate closing of resources

When a component _owns_ a resource there will be times that the resource needs recreating.  It's important to note that resources, by design, aren't meant to be stopped and started again.  Rather resources are meant to be started, closed and the recreated when necessary.

The `LifecycleCoordinator` can manage the close of these resources for you.  If you use the coordinator to create managed resources then it will automatically close any old resource before creating the new one.  It will also close the resources on `stop` or `close`.

Here is an example of creating a managed resource:

```kotlin
coordinator.createManagedResource(CONFIG_HANDLE) {
    configurationReadService.registerComponentForUpdates(
        coordinator,
        setOf(FLOW_CONFIG, MESSAGING_CONFIG)
    )
}
```

NOTE that it is still your responsibility to recreate resources when necessary.

For reference see the [LifecycleCoordinatorFactory API](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle/src/main/kotlin/net/corda/lifecycle/LifecycleCoordinatorFactory.kt).

## Using `LifecycleTest` to verify component lifecycle handling

The `LifecycleTest` class is designed to be a helper class for testing lifecycle
related behaviour for components.

For more detail on the methods you can use for testing see the [LifecycleTest API](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/libs/lifecycle/lifecycle-test-impl/src/main/kotlin/net/corda/lifecycle/test/impl/LifecycleTest.kt).

There are some specific testing scenarios that should be considered when testing
a component lifecycle:

- Startup tests, in order to ensure that the expected order of events leads to a correctly `UP` component.
- Shutdown tests, in order to ensure that stopping a component leads to the correct handling of internal objects.
- Handling of correct configuration from the config read service.
- Handling of changed configuration from the config read service.
- Handling of incorrect configuration from the config read service.
- Handling of a dependency in the `ERROR` state.
- Repeated `UP`/`DOWN`/`ERROR` switching of the component's dependencies.  Ideally this would
be for each dependency - certainly if the behaviour of your component differs based on
the dependency.
- Verification of dependency ordering constraints.  If your component needs configuration _before_ relying on a dependency
will it correctly handle if the configuration comes after the dependency is `UP`?  Or if other dependencies come up in
different orders?
