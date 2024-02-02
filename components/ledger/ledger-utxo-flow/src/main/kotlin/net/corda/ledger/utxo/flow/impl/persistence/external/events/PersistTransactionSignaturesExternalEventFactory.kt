package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistTransactionSignatures
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistTransactionSignaturesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistTransactionSignaturesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistTransactionSignaturesParameters): Any {
        return PersistTransactionSignatures(
            parameters.id,
            parameters.startingIndex,
            parameters.signatures.map { ByteBuffer.wrap(it) }
        )
    }
}

data class PersistTransactionSignaturesParameters(
    val id: String,
    val startingIndex: Int,
    val signatures: List<ByteArray>
)
