package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistFilteredTransactionsAndSignatures
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistFilteredTransactionsExternalEventFactory : AbstractUtxoLedgerExternalEventFactory<PersistFilteredTransactionParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistFilteredTransactionParameters): Any {
        return PersistFilteredTransactionsAndSignatures(ByteBuffer.wrap(parameters.filteredTransactionsAndSignaturesMap))
    }
}

@CordaSerializable
data class PersistFilteredTransactionParameters(
    val filteredTransactionsAndSignaturesMap: ByteArray
)
