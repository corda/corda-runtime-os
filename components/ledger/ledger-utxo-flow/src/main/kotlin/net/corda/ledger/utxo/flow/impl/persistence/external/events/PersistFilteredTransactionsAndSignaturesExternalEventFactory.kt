package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistFilteredTransactionsAndSignatures
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistFilteredTransactionsAndSignaturesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistFilteredTransactionsAndSignaturesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)
    override fun createRequest(parameters: PersistFilteredTransactionsAndSignaturesParameters): Any {
        return PersistFilteredTransactionsAndSignatures(parameters.filteredTransactionsAndSignatures)
    }
}

data class PersistFilteredTransactionsAndSignaturesParameters(
    val filteredTransactionsAndSignatures: ByteBuffer,
)
