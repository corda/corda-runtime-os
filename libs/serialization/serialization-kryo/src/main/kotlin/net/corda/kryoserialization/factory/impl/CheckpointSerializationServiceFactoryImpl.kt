package net.corda.kryoserialization.factory.impl

import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.CheckpointSerializationService
import net.corda.kryoserialization.CheckpointSerializer
import net.corda.kryoserialization.factory.CheckpointSerializationServiceFactory
import net.corda.kryoserialization.impl.CheckpointSerializationServiceImpl
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [CheckpointSerializationServiceFactory::class])
class CheckpointSerializationServiceFactoryImpl : CheckpointSerializationServiceFactory {

    override fun createCheckpointSerializationService(
        context: CheckpointSerializationContext,
        serializer: CheckpointSerializer
    ): CheckpointSerializationService {
       return CheckpointSerializationServiceImpl(context, serializer)
    }
}