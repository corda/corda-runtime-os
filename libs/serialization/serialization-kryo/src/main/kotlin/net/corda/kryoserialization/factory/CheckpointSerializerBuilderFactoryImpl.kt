package net.corda.kryoserialization.factory

import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(immediate = true, service = [CheckpointSerializerBuilderFactory::class])
class CheckpointSerializerBuilderFactoryImpl @Activate constructor(
    @Reference
    private val cryptoLibraryFactory: CryptoLibraryFactory
) : CheckpointSerializerBuilderFactory {
    override fun createCheckpointSerializerBuilder(
        sandboxGroup: SandboxGroup
    ): CheckpointSerializerBuilder {
        return KryoCheckpointSerializerBuilderImpl(
            cryptoLibraryFactory.getKeyEncodingService(),
            sandboxGroup
        )
    }
}
