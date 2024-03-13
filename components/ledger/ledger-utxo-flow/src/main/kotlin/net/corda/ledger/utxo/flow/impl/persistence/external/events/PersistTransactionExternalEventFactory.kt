package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant

@Component(service = [ExternalEventFactory::class])
class PersistTransactionExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<PersistTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionParameters): Any {
        val transaction = ByteBuffer.wrap(parameters.transaction)
        return PersistTransaction(
            transaction,
            parameters.transactionStatus.value,
            parameters.visibleStatesIndexes,
            parameters.lastPersistedTimestamp
        )
    }
}

@CordaSerializable
data class PersistTransactionParameters(
    val transaction: ByteArray,
    val transactionStatus: TransactionStatus,
    val visibleStatesIndexes: List<Int>,
    val lastPersistedTimestamp: Instant?
)
