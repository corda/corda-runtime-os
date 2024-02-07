package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.crypto.core.bytes
import net.corda.data.crypto.SecureHash
import net.corda.data.ledger.persistence.FindFilteredTransactionsAndSignatures
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.ledger.utxo.StateRef
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindFilteredTransactionsAndSignaturesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindFilteredTransactionsAndSignaturesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)
    override fun createRequest(parameters: FindFilteredTransactionsAndSignaturesParameters): Any {
        return FindFilteredTransactionsAndSignatures(
            parameters.stateRefs.map {
                net.corda.data.ledger.utxo.StateRef(
                    SecureHash(
                        it.transactionId.algorithm,
                        ByteBuffer.wrap(it.transactionId.bytes)
                    ),
                    it.index
                )
            }
        )
    }
}

data class FindFilteredTransactionsAndSignaturesParameters(
    val stateRefs: List<StateRef>
)
