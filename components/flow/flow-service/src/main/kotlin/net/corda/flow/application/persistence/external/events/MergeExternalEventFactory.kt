package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.MergeEntity
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class MergeExternalEventFactory : AbstractPersistenceExternalEventFactory<MergeParameters>() {

    override fun createRequest(parameters: MergeParameters): Any {
        return MergeEntity(parameters.serializedEntity)
    }
}

data class MergeParameters(val serializedEntity: ByteBuffer)