package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.FindEntity
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class FindExternalEventFactory : AbstractPersistenceExternalEventFactory<FindParameters>() {

    override fun createRequest(parameters: FindParameters): Any {
        return FindEntity(parameters.entityClass.canonicalName, parameters.serializedPrimaryKey)
    }
}

data class FindParameters(val entityClass: Class<*>, val serializedPrimaryKey: ByteBuffer)