package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistFilteredTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.ledger.common.data.transaction.TransactionStatus
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistFilteredTransactionExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<PersistFilteredTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistFilteredTransactionParameters): Any {
        return PersistFilteredTransaction(
            parameters.transactionId,
            parameters.transactionStatus,
            parameters.privacySalt,
            parameters.account,
            parameters.metadataHash
        )
    }
}

// TODO Temporary, need cleanup
data class PersistFilteredTransactionParameters(
    val transactionId: String,
    val transactionStatus: String,
    val privacySalt: ByteBuffer,
    val account: String,
    val metadataHash: String
)