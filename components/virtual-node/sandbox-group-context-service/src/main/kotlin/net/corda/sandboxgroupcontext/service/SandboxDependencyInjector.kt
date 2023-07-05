package net.corda.sandboxgroupcontext.service

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.serialization.SingletonSerializeAsToken

interface SandboxDependencyInjector<T: Any> : AutoCloseable {

    /**
     * Set any property on the obj e.g. flow or contract marked with @[CordaInject] with an instance of the type specified.
     * @param obj The obj to receive the injected services.
     */
    fun injectServices(obj: T)

    /**
     * @return A collection of services registered with the injector.
     */
    fun getRegisteredServices(): Collection<SingletonSerializeAsToken>
}