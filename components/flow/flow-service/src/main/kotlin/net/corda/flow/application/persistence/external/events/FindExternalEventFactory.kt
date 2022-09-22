package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.FindEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class FindExternalEventFactory : AbstractPersistenceExternalEventFactory<FindParameters>() {

    override fun createRequest(parameters: FindParameters): Any {
        return FindEntities(parameters.entityClass.canonicalName, parameters.serializedPrimaryKeys)
    }
}

data class FindParameters(val entityClass: Class<*>, val serializedPrimaryKeys: List<ByteBuffer>)