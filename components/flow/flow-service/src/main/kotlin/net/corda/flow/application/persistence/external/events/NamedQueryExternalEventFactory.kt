package net.corda.flow.application.persistence.external.events

import java.nio.ByteBuffer
import net.corda.data.persistence.FindWithNamedQuery
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class NamedQueryExternalEventFactory : AbstractPersistenceExternalEventFactory<NamedQueryParameters>() {

    override fun createRequest(parameters: NamedQueryParameters): Any {
        return FindWithNamedQuery(parameters.queryName, parameters.parameters, parameters.offset, parameters.limit)
    }
}

data class NamedQueryParameters(val queryName: String, val parameters: Map<String, ByteBuffer>, val offset: Int, val limit: Int)