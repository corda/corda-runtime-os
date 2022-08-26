package net.corda.flow.application.persistence.external.events

import net.corda.data.persistence.FindAll
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class FindAllExternalEventFactory : AbstractPersistenceExternalEventFactory<FindAllParameters>() {

    override fun createRequest(parameters: FindAllParameters): Any {
        return FindAll(parameters.entityClass.canonicalName, parameters.offset, parameters.limit)
    }
}

data class FindAllParameters(val entityClass: Class<*>, val offset: Int, val limit: Int)