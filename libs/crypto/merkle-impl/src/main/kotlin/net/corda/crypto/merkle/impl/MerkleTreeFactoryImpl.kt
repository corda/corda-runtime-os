package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [ MerkleTreeFactory::class, UsedByFlow::class ], scope = PROTOTYPE)
class MerkleTreeFactoryImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider
) : MerkleTreeFactory, UsedByFlow, SingletonSerializeAsToken {
    override fun createTree(leaves: List<ByteArray>, digest: MerkleTreeHashDigest): MerkleTree =
        when (digest) {
            is MerkleTreeHashDigestProvider -> merkleTreeProvider.createTree(leaves, digest)
            else -> throw CordaRuntimeException("An instance of MerkleTreeHashDigestProvider is required when " +
                    "creating a Merkle tree, but received ${digest.javaClass.name} instead.")
        }

    override fun createHashDigest(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<String, Any>,
    ): MerkleTreeHashDigest =
        merkleTreeProvider.createHashDigestProvider(merkleTreeHashDigestProviderName, digestAlgorithmName, options)
}
