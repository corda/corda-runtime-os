# Using `LifecycleTest` to verify component lifecycle handling

The `LifecycleTest` class is designed to be a helper class for testing lifecycle
related behaviour for components.

For more detail on the methods you can use for testing see the `LifecycleTest` API.

There are some specific testing scenarios that should be considered when testing
a component lifecycle:

- Repeated `UP`/`DOWN` switching of the component's dependencies.  Ideally this would
be for each dependency - certainly if the behaviour of your component differs based on
the dependency.
- Handling of correct configuration from the config read service.
- Handling of incorrect configuration from the config read service.

For the configuration there is API added to "inject" the configuration into the component.
