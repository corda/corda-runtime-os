package net.corda.flow.external.events.impl.handler

import net.corda.flow.external.events.handler.ExternalEventHandler
import net.corda.flow.external.events.impl.handler.ExternalEventHandlerMap.Companion.EXTERNAL_EVENT_HANDLERS
import net.corda.flow.pipeline.exceptions.FlowFatalException
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(
    service = [ExternalEventHandlerMap::class],
    reference = [
        Reference(
            name = EXTERNAL_EVENT_HANDLERS,
            service = ExternalEventHandler::class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
        )
    ]
)
class ExternalEventHandlerMap @Activate constructor(private val componentContext: ComponentContext) {

    internal companion object {
        const val EXTERNAL_EVENT_HANDLERS = "externalEventHandlers"
        private fun <T> ComponentContext.fetchServices(refName: String): List<T> {
            @Suppress("unchecked_cast")
            return (locateServices(refName) as? Array<T>)?.toList() ?: emptyList()
        }
    }

    private val handlers: Map<String, ExternalEventHandler<Any, Any?, Any>> by lazy {
        componentContext
            .fetchServices<ExternalEventHandler<Any, Any?, Any>>(EXTERNAL_EVENT_HANDLERS)
            .associateBy { it::class.java.name }
    }

    fun get(handlerClassName: String): ExternalEventHandler<Any, Any?, *> {
        return handlers[handlerClassName]
            ?: throw FlowFatalException("$handlerClassName does not have an associated external event handler")
    }
}