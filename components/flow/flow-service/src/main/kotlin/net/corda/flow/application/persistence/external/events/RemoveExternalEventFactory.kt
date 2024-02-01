package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.DeleteEntities
import net.corda.utilities.toByteBuffers
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class RemoveExternalEventFactory : AbstractPersistenceExternalEventFactory<RemoveParameters>() {

    override fun createRequest(parameters: RemoveParameters): Any {
        return DeleteEntities(parameters.serializedEntities.toByteBuffers())
    }
}

@CordaSerializable
data class RemoveParameters(val serializedEntities: List<ByteArray>)