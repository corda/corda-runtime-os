package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransactionIds
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindTransactionIdsExternalEventFactory
    : AbstractUtxoLedgerExternalEventFactory<FindTransactionIdsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionIdsParameters): Any {
        return FindTransactionIds(parameters.transactionIds, parameters.statuses.map { it.value })
    }
}

data class FindTransactionIdsParameters(
    val transactionIds: List<String>,
    val statuses: List<TransactionStatus>
)
