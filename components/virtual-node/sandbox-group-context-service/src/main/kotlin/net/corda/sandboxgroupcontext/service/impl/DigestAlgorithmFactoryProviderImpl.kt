package net.corda.sandboxgroupcontext.service.impl

import net.corda.crypto.core.DigestAlgorithmFactoryProvider
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Suppress("unused")
@Component(
    service = [
        DigestAlgorithmFactoryProvider::class,
        SingletonSerializeAsToken::class,
        CustomMetadataConsumer::class
    ],
    scope = PROTOTYPE
)
class DigestAlgorithmFactoryProviderImpl @Activate constructor()
    : DigestAlgorithmFactoryProvider, SingletonSerializeAsToken, CustomMetadataConsumer {
    private val provider = mutableMapOf<String, DigestAlgorithmFactory>()

    override fun accept(context: MutableSandboxGroupContext) {
        context.getObjectByKey<Iterable<DigestAlgorithmFactory>>(DigestAlgorithmFactory::class.java.name)
            ?.forEach { factory ->
                provider[factory.algorithm] = factory
            }
    }

    override fun get(algorithmName: String): DigestAlgorithmFactory? {
        return provider[algorithmName]
    }
}
