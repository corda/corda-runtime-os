package net.corda.virtualnode.manager.impl

import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.virtualnode.manager.api.RuntimeRegistration
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.virtualnode.manager.service.api.DigestAlgorithmFactoryProviderMediator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This component allows us to register the custom crypto classes in a cpi / [SandboxGroup] with the underlying
 * crypto library in the Corda runtime.
 *
 * Instantiation of this class, requires the [DigestAlgorithmFactoryProviderMediator] which registers a callback
 * with the crypto libraries on instantiation.
 */
@Component(
    name = "digest.algorithm.factory.provider.registration",
    service = [RuntimeRegistration::class]
)
class DigestAlgorithmFactoryProviderRegistration @Activate constructor(
    @Reference
    private val mutableDigestAlgorithmFactoryProvider: DigestAlgorithmFactoryProviderMediator
) : RuntimeRegistration {
    companion object {
        private val log = contextLogger()
    }

    /**
     * Register any custom crypto classes in the cpi / [SandboxGroup] with the underlying Corda runtime libraries.
     */
    override fun register(sandboxGroup: SandboxGroup) {
        sandboxGroup.cpks.forEach { cpk ->
            // iterate over the entries in the jar manifest
            cpk.metadata.cordappManifest.digestAlgorithmFactories.forEach { digestAlgorithmFactoryClassName ->
                try {
                    val clazz = sandboxGroup.loadClassFromMainBundles(
                        digestAlgorithmFactoryClassName,
                        DigestAlgorithmFactory::class.java
                    )
                    val digestAlgorithmFactory = clazz.getDeclaredConstructor().newInstance()
                    mutableDigestAlgorithmFactoryProvider.addFactory(sandboxGroup, digestAlgorithmFactory)
                } catch (e: SandboxException) {
                    log.error("Cannot add digest factory for sandbox group $sandboxGroup")
                    throw e
                }
            }
        }
    }

    /**
     * Remove the registration of any custom crypto libraries for the given [SandboxGroup]
     */
    override fun unregister(sandboxGroup: SandboxGroup) {
        mutableDigestAlgorithmFactoryProvider.removeFactories(sandboxGroup)
    }
}
