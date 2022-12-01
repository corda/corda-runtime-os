package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.UpdateTransactionStatus
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.flow.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class UpdateTransactionStatusExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<UpdateTransactionStatusParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: UpdateTransactionStatusParameters): Any {
        return UpdateTransactionStatus(parameters.id, parameters.transactionStatus.value)
    }
}

data class UpdateTransactionStatusParameters(val id: String, val transactionStatus: TransactionStatus)