package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistFilteredTransactionsIfDoNotExist
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistFilteredTransactionsIfDoNotExistExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistFilteredTransactionsIfDoNotExistParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistFilteredTransactionsIfDoNotExistParameters): Any {
        return PersistFilteredTransactionsIfDoNotExist(parameters.filteredTransactions)
    }
}

data class PersistFilteredTransactionsIfDoNotExistParameters(
    val filteredTransactions: ByteBuffer
)