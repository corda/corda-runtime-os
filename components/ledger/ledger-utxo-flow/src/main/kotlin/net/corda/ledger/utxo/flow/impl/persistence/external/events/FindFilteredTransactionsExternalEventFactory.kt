package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindFilteredTransactions
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindFilteredTransactionsExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindFilteredTransactionsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindFilteredTransactionsParameters): Any {
        return FindFilteredTransactions(parameters.transactionIds)
    }
}

data class FindFilteredTransactionsParameters(
    val transactionIds: List<String>
)