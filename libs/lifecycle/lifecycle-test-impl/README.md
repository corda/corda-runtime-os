# Component Lifecycles

The 'Lifecycle' library is provided as a mechanism to allow components access to a small state machine, 
providing a single threaded response for event handling.  Such events can be (but are not limited to):

- `UP`/`DOWN`/`ERROR` status
- Configuration change notifications
- Any other dynamic events that a component might want to react to

By providing this simple state machine components can expect all incoming events to be provided from 
within a single thread.  This removes the need for some concurrency handling.

## Configuration management using lifecycles

Configuration management is one of the most common use cases of the lifecycle updates that a component
will use - specifically, being notified and adjusting for configuration changes.  For this reason the
`ConfigurationReadService` has been developed to unify configuration handling.

When registering your component for configuration prefer to use `registerComponentForUpdates` with your component
and the set of configuration keys that your component needs to use.  This API will ensure that you are given the
configuration you specifically want.  In addition, it is more robust than the previous version, which is now deprecated.

For more detail, follow the API for `ConfigurationReadService`.

## Using `DependentComponents.of` to specify dependent components

Additionally, your component may have a set of dependencies upon which it relies and also which can be started/stopped
as a single unit.  This is likely for specific dependencies which are injected and only expected to be used by your
component.

There is a helper utility provided for these classes called `DependentComponents`.  When specifying your dependencies
via this utility you can more simply register/start and stop all dependencies.

For more detail, follow the API for `DependentComponents`.


## Using `LifecycleTest` to verify component lifecycle handling

The `LifecycleTest` class is designed to be a helper class for testing lifecycle
related behaviour for components.

For more detail on the methods you can use for testing see the `LifecycleTest` API.

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
