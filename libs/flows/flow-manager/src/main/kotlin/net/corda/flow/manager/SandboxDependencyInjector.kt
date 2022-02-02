package net.corda.flow.manager

import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * The Sandbox dependency injector is responsible for injecting services into CordApp flows.
 */
interface SandboxDependencyInjector : NonSerializable {

    /**
     * Set any property on the flow marked with @[CordaInject] with an instance of the type specified.
     * @param flow The flow to receive the injected services.
     */
    fun injectServices(flow: Flow<*>)

    /**
     * @return A list of singletons registered with the injector.
     */
    fun getRegisteredSingletons(): Set<SingletonSerializeAsToken>
}