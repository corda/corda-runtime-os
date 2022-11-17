package net.corda.sandbox.crypto

import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getMetadataServices
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("unused")
@Component(
    service = [
        DigestAlgorithmFactoryProvider::class,
        UsedByFlow::class,
        UsedByPersistence::class,
        UsedByVerification::class
    ],
    scope = PROTOTYPE
)
class DigestAlgorithmFactoryProviderImpl @Activate constructor()
    : DigestAlgorithmFactoryProvider, UsedByFlow, UsedByPersistence, UsedByVerification, CustomMetadataConsumer {
    private val provider = mutableMapOf<String, DigestAlgorithmFactory>()

    override fun accept(context: MutableSandboxGroupContext) {
        context.getMetadataServices<DigestAlgorithmFactory>().forEach { factory ->
            provider[factory.algorithm] = factory
        }
    }

    override fun get(algorithmName: String): DigestAlgorithmFactory? {
        return provider[algorithmName]
    }
}
