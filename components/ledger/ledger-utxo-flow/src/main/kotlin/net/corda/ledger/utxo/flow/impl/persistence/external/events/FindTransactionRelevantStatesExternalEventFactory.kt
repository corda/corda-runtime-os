package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransactionRelevantStates
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindTransactionRelevantStatesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindTransactionRelevantStatesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionRelevantStatesParameters): Any {
        return FindTransactionRelevantStates(parameters.id)
    }
}

data class FindTransactionRelevantStatesParameters(val id: String)