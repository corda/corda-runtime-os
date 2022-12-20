package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindUnconsumedStatesByType
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindUnconsumedStatesByTypeExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FindUnconsumedStatesByTypeParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindUnconsumedStatesByTypeParameters): Any {
        return FindUnconsumedStatesByType(parameters.stateClass.canonicalName, parameters.jPath)
    }
}

data class FindUnconsumedStatesByTypeParameters(
    val stateClass: Class<*>,
    val jPath: String? = null
)