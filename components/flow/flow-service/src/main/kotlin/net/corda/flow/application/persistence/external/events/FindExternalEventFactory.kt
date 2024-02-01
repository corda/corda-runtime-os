package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.FindEntities
import net.corda.utilities.toByteBuffers
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class FindExternalEventFactory : AbstractPersistenceExternalEventFactory<FindParameters>() {

    override fun createRequest(parameters: FindParameters): Any {
        return FindEntities(parameters.entityClass.canonicalName, parameters.serializedPrimaryKeys.toByteBuffers())
    }
}

@CordaSerializable
data class FindParameters(val entityClass: Class<*>, val serializedPrimaryKeys: List<ByteArray>)