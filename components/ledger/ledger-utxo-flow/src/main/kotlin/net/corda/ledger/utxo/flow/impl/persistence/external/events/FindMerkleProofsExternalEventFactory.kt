package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.FindMerkleProofs
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class FindMerkleProofsExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<FindMerkleProofsParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: FindMerkleProofsParameters): Any {
        return FindMerkleProofs(
            parameters.transactionId,
            parameters.groupId
        )
    }
}

data class FindMerkleProofsParameters(
    val transactionId: String,
    val groupId: Int
)