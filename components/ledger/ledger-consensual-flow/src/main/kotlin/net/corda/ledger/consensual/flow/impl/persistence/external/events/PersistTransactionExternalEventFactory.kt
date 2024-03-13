package net.corda.ledger.consensual.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant

@Component(service = [ExternalEventFactory::class])
class PersistTransactionExternalEventFactory :
    AbstractConsensualLedgerExternalEventFactory<PersistTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionParameters): Any {
        return PersistTransaction(
            ByteBuffer.wrap(parameters.transaction),
            parameters.transactionStatus,
            emptyList(),
            null
        )
    }
}

@CordaSerializable
data class PersistTransactionParameters(
    val transaction: ByteArray,
    val transactionStatus: String,
    val lastPersistedTimestamp: Instant?
)
