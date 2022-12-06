package net.corda.kryoserialization.factory

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [CheckpointSerializerBuilderFactory::class])
class CheckpointSerializerBuilderFactoryImpl @Activate constructor(
    @Reference
    private val keyEncodingService: KeyEncodingService
) : CheckpointSerializerBuilderFactory {
    override fun createCheckpointSerializerBuilder(
        sandboxGroup: SandboxGroup
    ): CheckpointSerializerBuilder {
        return KryoCheckpointSerializerBuilderImpl(
            keyEncodingService,
            sandboxGroup
        )
    }
}
