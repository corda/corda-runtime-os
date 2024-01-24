package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ MerkleProofFactory::class, UsedByFlow::class ],
    scope = ServiceScope.PROTOTYPE
)
class MerkleProofFactoryImpl @Activate constructor()
    : MerkleProofFactory, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun createAuditMerkleProof(
        transactionId: String,
        groupId: Int,
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>
    ): MerkleProof {
        return MerkleProofImpl(
            MerkleProofType.AUDIT,
            treeSize,
            leavesIndexAndData.map { (leafIndex, data) ->
                IndexedMerkleLeafImpl(
                    leafIndex,
                    // TODO CORE-18698 For now this is null as we don't have access to the transaction's metadata to calculate
                    null,
                    data
                )
            },
            hashes
        )
    }
}