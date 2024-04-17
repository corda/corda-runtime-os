package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.PersistEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.utilities.toByteBuffers
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class PersistExternalEventFactory : AbstractPersistenceExternalEventFactory<PersistParameters>() {

    override fun createRequest(parameters: PersistParameters): Any {
        return PersistEntities(parameters.serializedEntities.toByteBuffers())
    }
}

@CordaSerializable
data class PersistParameters(
    val deduplicationId: String,
    val serializedEntities: List<ByteArray>
)
