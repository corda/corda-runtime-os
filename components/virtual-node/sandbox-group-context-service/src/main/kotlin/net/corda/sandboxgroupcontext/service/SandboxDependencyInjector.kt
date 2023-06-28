package net.corda.sandboxgroupcontext.service

import net.corda.v5.serialization.SingletonSerializeAsToken

interface SandboxDependencyInjector: AutoCloseable {
    /**
     * @return A collection of services registered with the injector.
     */
    fun getRegisteredServices(): Collection<SingletonSerializeAsToken>
}