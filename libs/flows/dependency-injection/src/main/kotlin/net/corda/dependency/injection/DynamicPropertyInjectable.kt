package net.corda.dependency.injection

/**
 * [DynamicPropertyInjectable] can be implemented by any corda injectable class which needs to have a reference to a property whose value
 * may change at service injection points.
 *
 * For example, a service may require a reference to [net.corda.v5.application.internal.FlowStateMachine] which would be set when the class
 * is instantiated, but would also need to be updated after checkpointing so that the correct reference is held by the service.
 * Implementing this interface allows the injection functionality to identify the classes which require these dynamic properties to be set
 * if they are present.
 */
interface DynamicPropertyInjectable<T> {
    var injectableProperty: T
}