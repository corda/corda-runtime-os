package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistTransactionIfDoesNotExist
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.base.annotations.CordaSerializable
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
        return PersistTransactionIfDoesNotExist(ByteBuffer.wrap(parameters.transaction), parameters.transactionStatus.value)
    }
}

@CordaSerializable
data class PersistTransactionIfDoesNotExistParameters(val transaction: ByteArray, val transactionStatus: TransactionStatus)
