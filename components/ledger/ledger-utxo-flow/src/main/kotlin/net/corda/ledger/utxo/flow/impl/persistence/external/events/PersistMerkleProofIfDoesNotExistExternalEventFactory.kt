package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.PersistMerkleProofIfDoesNotExist
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.crypto.merkle.MerkleProof
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Clock

@Component(service = [ExternalEventFactory::class])
class PersistMerkleProofIfDoesNotExistExternalEventFactory :
    AbstractUtxoLedgerExternalEventFactory<PersistMerkleProofIfDoesNotExistParameters> {
    @Activate
    constructor() : super()
    constructor(clock: Clock) : super(clock)

    override fun createRequest(parameters: PersistMerkleProofIfDoesNotExistParameters): Any {
        return PersistMerkleProofIfDoesNotExist(
            parameters.transactionId,
            parameters.groupId,
            parameters.merkleProof.treeSize,
            parameters.merkleProof.leaves.map { it.index },
            parameters.merkleProof.hashes.map { it.toString() }
        )
    }
}

data class PersistMerkleProofIfDoesNotExistParameters(
    val transactionId: String,
    val groupId: Int,
    val merkleProof: MerkleProof
)
