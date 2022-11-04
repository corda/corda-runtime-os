package net.corda.sandboxgroupcontext.service

import net.corda.lifecycle.Lifecycle
import net.corda.sandboxgroupcontext.CustomMetadataConsumer
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory
import net.corda.v5.serialization.SerializationCustomSerializer

interface SandboxGroupContextComponent : SandboxGroupContextService, CacheConfiguration, Lifecycle

/**
 * This function registers any [DigestAlgorithmFactory][net.corda.v5.crypto.extensions.DigestAlgorithmFactory]
 * instances that exist inside the [SandboxGroup][net.corda.sandbox.SandboxGroup]'s CPKs.
 * The [DigestAlgorithmFactoryProvider][net.corda.crypto.core.DigestAlgorithmFactoryProvider]
 * component will discover these services via its [CustomMetadataConsumer]
[net.corda.sandboxgroupcontext.CustomMetadataConsumer] interface.
 *
 * @param sandboxGroupContext
 *
 * @return an [AutoCloseable] for unregistering the services.
 */
fun SandboxGroupContextComponent.registerCustomCryptography(sandboxGroupContext: SandboxGroupContext): AutoCloseable {
    return registerMetadataServices(
        sandboxGroupContext,
        serviceNames = { metadata -> metadata.cordappManifest.digestAlgorithmFactories },
        serviceMarkerType = DigestAlgorithmFactory::class.java
    )
}

fun SandboxGroupContextComponent.registerCordappCustomSerializers(sandboxGroupContext: SandboxGroupContext): AutoCloseable {
    return registerMetadataServices(
        sandboxGroupContext,
        serviceNames = { metadata -> metadata.cordappManifest.serializers },
        serviceMarkerType = SerializationCustomSerializer::class.java
    )
}
