package net.corda.ledger.lib.impl.stub.external.event

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory

class ExternalEventCallback(
    private val callback: (Class<out ExternalEventFactory<*, *, *>>, Any) -> Any
) : ExternalEventExecutor {

    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        @Suppress("UNCHECKED_CAST")
        return callback(factoryClass, parameters) as RESUME;
    }
}