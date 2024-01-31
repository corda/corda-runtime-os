package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.FindEntities
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class FindExternalEventFactory : AbstractPersistenceExternalEventFactory<FindParameters>() {

    override fun createRequest(parameters: FindParameters): Any {
        return FindEntities(parameters.entityClass.canonicalName, parameters.serializedPrimaryKeys)
    }
}

@CordaSerializable
data class FindParameters(val entityClass: Class<*>, val serializedPrimaryKeys: List<ByteBuffer>)