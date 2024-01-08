package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleProofFactory
import net.corda.crypto.core.SecureHashImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Component(
    service = [ MerkleProofFactory::class, UsedByFlow::class ],
    scope = ServiceScope.PROTOTYPE
)
class MerkleProofFactoryImpl @Activate constructor()
    : MerkleProofFactory, UsedByFlow, SingletonSerializeAsToken {

    companion object {
        val log = LoggerFactory.getLogger(MerkleProofFactoryImpl::class.java)
    }

    @Suspendable
    override fun createMerkleProof(
        transactionId: String,
        groupId: Int,
        treeSize: Int,
        leavesIndexAndData: Map<Int, ByteArray>,
        hashes: List<SecureHash>
    ): MerkleProof {
        log.info("[MerkleProofPoC] creating merkle proof in factory! $transactionId, $groupId, $treeSize, $leavesIndexAndData, $hashes")
        val mp = MerkleProofImpl(
            MerkleProofType.AUDIT,
            treeSize,
            leavesIndexAndData.map { (leafIndex, data) -> IndexedMerkleLeafImpl(leafIndex, null, data) }, // TODO what is nonce?!
            hashes
        )

        log.info("[MerkleProofPoC] created MP: $mp")

        return mp
    }
}