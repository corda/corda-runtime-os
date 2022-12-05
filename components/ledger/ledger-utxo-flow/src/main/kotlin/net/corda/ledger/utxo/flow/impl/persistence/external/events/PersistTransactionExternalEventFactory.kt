package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.ComponentPosition
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistTransactionExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionParameters): Any {
        return PersistTransaction(parameters.transaction, parameters.transactionStatus, parameters.relevantStates)
    }
}

data class PersistTransactionParameters(
    val transaction: ByteBuffer,
    val transactionStatus: String,
    val relevantStates: List<ComponentPosition>
)