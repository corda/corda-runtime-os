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
        return FindUnconsumedStatesByType(parameters.transactionId, parameters.stateClass.canonicalName)
    }
}

data class FindUnconsumedStatesByTypeParameters(
    val transactionId: String,
    val stateClass: Class<*>
)