package net.corda.virtualnode.manager.service.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.crypto.DigestAlgorithmFactoryProviderRegistry
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.virtualnode.manager.service.api.DigestAlgorithmFactoryProviderMediator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of the [DigestAlgorithmFactoryProviderMediator] interface.
 *
 * This class acts as a mediator (or bridge?) between the Crypto libraries and the sandbox libraries.
 *
 * It holds a map of all [SandboxGroup] instances to custom crypto digests (plural).
 *
 * When this service starts it registers itself with the crypto [DigestAlgorithmFactoryProviderRegistry]
 * as a callback.
 *
 * Then, when the virtual node manager (tbc) starts, it reads any custom [DigestAlgorithmFactory] implementations
 * from a cordapp, and registers them with this class.
 *
 * When there is a call to the [factories()] method, we use the [SandboxContextService] to walk the stack
 * and return the correct custom crypto digests for the calling [SandboxGroup].
 *
 * This service does NOT want to be part of crypto as it uses [SandboxContextService] and would introduce
 * unnecessary coupling between the bundles / packages / jars / whatever.
 */
@Component(service = [DigestAlgorithmFactoryProviderMediator::class])
class DigestAlgorithmFactoryProviderMediatorImpl @Activate constructor(
    @Reference private val sandboxContextService: SandboxContextService,
    @Reference private val digestAlgorithmFactoryProviderRegistry: DigestAlgorithmFactoryProviderRegistry
) : DigestAlgorithmFactoryProviderMediator {
    companion object {
        val log = contextLogger()
    }

    // Trivial wrapper for the value-type of the main map.
    private class FactoryPerSandboxGroup {
        private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>()

        fun map(): Map<String, DigestAlgorithmFactory> = factories

        fun put(algorithmName: String, digestAlgorithmFactory: DigestAlgorithmFactory) {
            if (factories.containsKey(algorithmName)) {
                throw IllegalArgumentException("Digest name $algorithmName already exists - cannot register the same thing twice")
            }
            factories[algorithmName] = digestAlgorithmFactory
        }
    }

    /**
     * Map of [SandboxGroup] to the custom crypto digests in that sandbox group.
     */
    private val factoriesPerSandboxGroup = mutableMapOf<SandboxGroup, FactoryPerSandboxGroup>()

    /**
     * The [DigestAlgorithmFactoryProviderRegistry] service is instantiated in the core set of modules.
     * This class/service will be instantiated after, so we register ourselves with the existing
     * service as a callback.  Note that we also unregister if we're deactivated.
     */
    init {
        digestAlgorithmFactoryProviderRegistry.register { factoriesForCallingSandboxGroup() }
    }

    /**
     * When this bundle is deactivated (e.g. by being unloaded) we unregister ourselves with the [DigestAlgorithmFactoryProvider]
     */
    @Suppress("UNUSED")
    @Deactivate
    fun unregister() {
        digestAlgorithmFactoryProviderRegistry.unregister()
    }

    /**
     * Return only the factories for sandbox group of the caller.
     * The sandbox group of the caller is determined by the [SandboxContextService].
     */
    private fun factoriesForCallingSandboxGroup(): Map<String, DigestAlgorithmFactory> {
        try {
            val sandboxGroup = sandboxContextService.getCallingSandboxGroup()
            return factoriesPerSandboxGroup[sandboxGroup]?.map() ?: emptyMap()
        } catch (e: SandboxException) {
            log.error("Sandbox group for caller could not be determined", e)
        }
        return emptyMap()
    }

    /**
     * This function is intended to be called during the post-instantiation/bundle loading phase of a cordapp
     * within the virtual node manager and before any flows are executed.
     */
    override fun addFactory(sandboxGroup: SandboxGroup, digestAlgorithmFactory: DigestAlgorithmFactory) {
        factoriesPerSandboxGroup
            .computeIfAbsent(sandboxGroup) { FactoryPerSandboxGroup() }
            .put(digestAlgorithmFactory.algorithm, digestAlgorithmFactory)
        log.info("Registered ${digestAlgorithmFactory.algorithm} for $sandboxGroup")
    }

    override fun removeFactories(sandboxGroup: SandboxGroup) {
        factoriesPerSandboxGroup.remove(sandboxGroup)
    }
}
