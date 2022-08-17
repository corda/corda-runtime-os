package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.PersistEntity
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class PersistExternalEventFactory : AbstractPersistenceExternalEventFactory<PersistParameters>() {

    override fun createRequest(parameters: PersistParameters): Any {
        return PersistEntity(parameters.serializedEntity)
    }
}

data class PersistParameters(val serializedEntity: ByteBuffer)