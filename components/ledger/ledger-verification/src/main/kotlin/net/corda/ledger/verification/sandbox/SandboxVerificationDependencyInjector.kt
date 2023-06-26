package net.corda.ledger.verification.sandbox

import net.corda.serialization.checkpoint.NonSerializable
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * The Sandbox dependency injector is responsible for injecting services into CordApp flows.
 */
interface SandboxVerificationDependencyInjector : AutoCloseable, NonSerializable {

    /**
     * Set any property on the flow marked with @[CordaInject] with an instance of the type specified.
     * @param contract The flow to receive the injected services.
     */
    fun injectServices(contract: Contract)

    /**
     * @return A collection of services registered with the injector.
     */
    fun getRegisteredServices(): Collection<SingletonSerializeAsToken>
}