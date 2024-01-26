package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
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
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>,
        hashDigestProvider: MerkleTreeHashDigestProvider
    ): MerkleProof {
        return MerkleProofImpl(
            MerkleProofType.AUDIT,
            treeSize,
            leavesIndexAndData.map { (leafIndex, data) ->
                IndexedMerkleLeafImpl(
                    leafIndex,
                    hashDigestProvider.leafNonce(leafIndex),
                    data
                )
            },
            hashes
        )
    }
}
