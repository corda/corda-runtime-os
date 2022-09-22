package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.DeleteEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class RemoveExternalEventFactory : AbstractPersistenceExternalEventFactory<RemoveParameters>() {

    override fun createRequest(parameters: RemoveParameters): Any {
        return DeleteEntities(parameters.serializedEntities)
    }
}

data class RemoveParameters(val serializedEntities: List<ByteBuffer>)