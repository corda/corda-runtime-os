package net.corda.flow.external.events.impl.factory

import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.impl.factory.ExternalEventFactoryMap.Companion.EXTERNAL_EVENT_HANDLERS
import net.corda.flow.pipeline.exceptions.FlowFatalException
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(
    service = [ExternalEventFactoryMap::class],
    reference = [
        Reference(
            name = EXTERNAL_EVENT_HANDLERS,
            service = ExternalEventFactory::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ]
)
class ExternalEventFactoryMap @Activate constructor(private val componentContext: ComponentContext) {

    internal companion object {
        const val EXTERNAL_EVENT_HANDLERS = "externalEventFactories"
        private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
            @Suppress("unchecked_cast")
            return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
        }
    }

    private val externalEventFactories: Map<String, ExternalEventFactory<Any, Any?, Any>> by lazy {
        componentContext
            .fetchServices<ExternalEventFactory<Any, Any?, Any>>(EXTERNAL_EVENT_HANDLERS)
            .associateBy { it::class.java.name }
    }

    fun get(factoryClassName: String): ExternalEventFactory<Any, Any?, *> {
        return externalEventFactories[factoryClassName]
            ?: throw FlowFatalException("$factoryClassName does not have an associated external event factory")
    }
}