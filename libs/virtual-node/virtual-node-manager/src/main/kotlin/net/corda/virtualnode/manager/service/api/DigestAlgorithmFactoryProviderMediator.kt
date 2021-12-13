package net.corda.virtualnode.manager.service.api

import net.corda.sandbox.SandboxGroup
import net.corda.v5.cipher.suite.DigestAlgorithmFactory

/**
 * A simple mediator (bridge?  naming things...) for adding and removing custom digests, but separating
 * the crypto and sandbox implementations.
 *
 * Not in crypto, so crypto does not require an unnecessary dependency on [SandboxGroup]
 */
interface DigestAlgorithmFactoryProviderMediator {
    fun addFactory(sandboxGroup: SandboxGroup, digestAlgorithmFactory: DigestAlgorithmFactory)
    fun removeFactories(sandboxGroup: SandboxGroup)
}
