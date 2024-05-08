package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindTransactionIdsAndStatuses
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindSignedTransactionIdsAndStatusesExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindSignedTransactionIdsAndStatusesParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindSignedTransactionIdsAndStatusesParameters): Any {
        return FindTransactionIdsAndStatuses(parameters.transactionIds)
    }
}

@CordaSerializable
data class FindSignedTransactionIdsAndStatusesParameters(val transactionIds: List<String>)
