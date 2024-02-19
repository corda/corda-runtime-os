package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindSignedLedgerTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindSignedLedgerTransactionExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<FindSignedLedgerTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindSignedLedgerTransactionParameters): Any {
        return FindSignedLedgerTransaction(parameters.id, parameters.transactionStatus.value)
    }
}

@CordaSerializable
data class FindSignedLedgerTransactionParameters(val id: String, val transactionStatus: TransactionStatus)
