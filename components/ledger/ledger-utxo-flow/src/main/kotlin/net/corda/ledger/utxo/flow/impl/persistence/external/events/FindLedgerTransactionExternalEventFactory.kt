package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.v2.FindLedgerTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindLedgerTransactionExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FindLedgerTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindLedgerTransactionParameters): Any {
        return FindLedgerTransaction(parameters.id, parameters.transactionStatus.value)
    }
}

data class FindLedgerTransactionParameters(val id: String, val transactionStatus: TransactionStatus)