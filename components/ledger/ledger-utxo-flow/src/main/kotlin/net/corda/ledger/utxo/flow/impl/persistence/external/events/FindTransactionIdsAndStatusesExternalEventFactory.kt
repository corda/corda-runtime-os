package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransactionIdsAndStatuses
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindTransactionIdsAndStatusesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindTransactionIdsAndStatusesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindTransactionIdsAndStatusesParameters): Any {
        return FindTransactionIdsAndStatuses(parameters.transactionIds)
    }
}

@CordaSerializable
data class FindTransactionIdsAndStatusesParameters(val transactionIds: List<String>)
