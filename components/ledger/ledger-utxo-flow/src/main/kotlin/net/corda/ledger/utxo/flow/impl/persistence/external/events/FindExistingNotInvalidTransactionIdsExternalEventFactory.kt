package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindExistingNotInvalidTransactionIds
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindExistingNotInvalidTransactionIdsExternalEventFactory
    : AbstractUtxoLedgerExternalEventFactory<FindExistingNotInvalidTransactionIdsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindExistingNotInvalidTransactionIdsParameters): Any {
        return FindExistingNotInvalidTransactionIds(parameters.transactionIds)
    }
}

data class FindExistingNotInvalidTransactionIdsParameters(val transactionIds: List<String>)