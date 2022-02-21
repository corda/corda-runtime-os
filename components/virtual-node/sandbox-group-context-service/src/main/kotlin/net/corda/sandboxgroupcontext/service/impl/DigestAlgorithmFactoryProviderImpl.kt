package net.corda.sandboxgroupcontext.service.impl

import net.corda.crypto.DigestAlgorithmFactoryProvider
import net.corda.sandbox.SandboxContextService
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import org.osgi.framework.Bundle
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("unused")
@Component(
    service = [ DigestAlgorithmFactoryProvider::class ],
    scope = PROTOTYPE
)
class DigestAlgorithmFactoryProviderImpl @Activate constructor(
    @Reference
    private val sandboxContextService: SandboxContextService
) : DigestAlgorithmFactoryProvider {
    private val cleanups = mutableListOf<AutoCloseable>()

    private fun getAlgorithmFactories(bundle: Bundle): Map<String, DigestAlgorithmFactory>? {
        val bundleContext = bundle.bundleContext
        return bundleContext?.getServiceReferences(DigestAlgorithmFactory::class.java, CORDA_SANDBOX_FILTER)
            ?.mapNotNull { reference ->
                bundleContext.getService(reference)?.also {
                    cleanups.add(AutoCloseable {
                        bundleContext.ungetService(reference)
                    })
                }
            }?.associateBy(DigestAlgorithmFactory::algorithm)
    }

    // This property MUST be "lazy" so that it is executed from within the sandbox.
    // Note that SandboxContextService.getCallingSandboxGroup() works by walking the stack.
    private val provider: Map<String, DigestAlgorithmFactory> by lazy {
        sandboxContextService.getCallingSandboxGroup()?.let { sandboxGroup ->
            sandboxGroup.metadata.keys.firstOrNull()?.let(::getAlgorithmFactories)
        } ?: emptyMap()
    }

    @Deactivate
    fun done() {
        cleanups.forEach(AutoCloseable::close)
    }

    override fun get(algorithmName: String): DigestAlgorithmFactory? {
        return provider[algorithmName]
    }
}
