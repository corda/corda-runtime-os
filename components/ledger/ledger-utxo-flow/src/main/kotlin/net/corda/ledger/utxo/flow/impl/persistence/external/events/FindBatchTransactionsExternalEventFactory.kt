package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindBatchTransactions
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindBatchTransactionsExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FindBatchTransactionsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindBatchTransactionsParameters): Any {
        return FindBatchTransactions(
            parameters.ids,
            parameters.status.value
        )
    }
}

data class FindBatchTransactionsParameters(
    val ids: List<String>,
    val status: TransactionStatus
)