package net.corda.ledger.injector.sandbox

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * The Sandbox dependency injector is responsible for injecting services into CorDapp verification.
 */
interface SandboxVerificationDependencyInjector : AutoCloseable {

    /**
     * Set any property on the contract marked with @[CordaInject] with an instance of the type specified.
     * @param contract The contract to receive the injected services.
     */
    fun injectServices(contract: Contract)

    /**
     * @return A collection of services registered with the injector.
     */
    fun getRegisteredServices(): Collection<SingletonSerializeAsToken>
}