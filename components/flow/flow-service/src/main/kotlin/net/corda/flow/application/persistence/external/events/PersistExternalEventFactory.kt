package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.PersistEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class PersistExternalEventFactory : AbstractPersistenceExternalEventFactory<PersistParameters>() {

    override fun createRequest(parameters: PersistParameters): Any {
        return PersistEntities(listOf(parameters.serializedEntity))
    }
}

data class PersistParameters(val serializedEntity: ByteBuffer)