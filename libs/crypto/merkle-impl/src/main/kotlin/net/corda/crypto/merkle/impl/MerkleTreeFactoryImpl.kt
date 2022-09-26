package net.corda.crypto.merkle.impl

import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [MerkleTreeFactory::class, SingletonSerializeAsToken::class], scope = PROTOTYPE, property=["corda.system=true"])
class MerkleTreeFactoryImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider
) : MerkleTreeFactory, SingletonSerializeAsToken {
    override fun createTree(leaves: List<ByteArray>, digest: MerkleTreeHashDigest) =
        merkleTreeProvider.createTree(leaves, digest as MerkleTreeHashDigestProvider)

    override fun createHashDigest(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<String, Any>,
    ): MerkleTreeHashDigest =
        merkleTreeProvider.createHashDigestProvider(merkleTreeHashDigestProviderName, digestAlgorithmName, options)
}