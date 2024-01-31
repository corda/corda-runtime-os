package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.MergeEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class MergeExternalEventFactory : AbstractPersistenceExternalEventFactory<MergeParameters>() {

    override fun createRequest(parameters: MergeParameters): Any {
        return MergeEntities(parameters.serializedEntities)
    }
}

@CordaSerializable
data class MergeParameters(val serializedEntities: List<ByteBuffer>)