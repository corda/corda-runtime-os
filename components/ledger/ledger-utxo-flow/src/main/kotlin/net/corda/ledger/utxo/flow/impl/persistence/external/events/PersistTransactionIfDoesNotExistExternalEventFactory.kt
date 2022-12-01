package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.flow.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistTransactionIfDoesNotExistExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistTransactionIfDoesNotExistParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionIfDoesNotExistParameters): Any {
        return PersistTransactionIfDoesNotExist(parameters.transaction, parameters.transactionStatus.value)
    }
}

data class PersistTransactionIfDoesNotExistParameters(val transaction: ByteBuffer, val transactionStatus: TransactionStatus)