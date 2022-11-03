package net.corda.flow.pipeline.sandbox

import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow

/**
 * The Sandbox dependency injector is responsible for injecting services into CordApp flows.
 */
interface SandboxDependencyInjector : AutoCloseable, NonSerializable {

    /**
     * Set any property on the flow marked with @[CordaInject] with an instance of the type specified.
     * @param flow The flow to receive the injected services.
     */
    fun injectServices(flow: Flow)

    /**
     * @return A collection of services registered with the injector.
     */
    fun getRegisteredServices(): Collection<UsedByFlow>
}
