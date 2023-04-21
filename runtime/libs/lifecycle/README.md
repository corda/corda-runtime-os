# Lifecycle

This module defines the interfaces and classes to coordinate Corda components.

The `net.corda.lifecycle.LifeCycleCoordinator` implementations schedule `net.corda.lifecycle.LifeCycleEvent` objects
processed by `lifeCycleProcessor`, a lambda function parameter of the coordinator.

The `lifeCycleProcessor` executes the proper logic according the type of `net.corda.lifecycle.LifeCycleEvent`.

*EXAMPLE*

```kotlin
SimpleLifeCycleCoordinator(64, 1000L) { event: LifeCycleEvent, coordinator: LifeCycleCoordinator ->
    when (event) {
        is StartEvent -> {
            // START THE COMPONENT 
        }
        is ErrorEvent -> {
            // HANDLE THE event.cause IF AN ERROR IS NOTIFIED
            // Exception thrown here, in the processor, will stop the coordinator. 
        }
        is PostEvent -> {
            // DO SOMETHING
        }
        is TimerEvent -> {
            // DO SOMETHING AT THE RIGHT TIME
        }
        is StopEvent -> {
            // STOP THE COMPONENT
        }
    }
}.use { coordinator ->
    coordinator.start()
    coordinator.postEvent(object : PostEvent {})
    val onTime = object : TimerEvent {
        override val key: String
            get() = "this_is_a_timer_event_5_seconds_after"
    }
    coordinator.setTimer(onTime.key, 5000L) { onTime }
}
```

See KDoc in source code for additional info.