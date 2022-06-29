package net.corda.crypto.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleService
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [MerkleService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class MerkleServiceImpl @Activate constructor(
    @Reference(service = DigestService::class)
    private val digestService: DigestService
) : MerkleService, SingletonSerializeAsToken {
    override fun createTree(leaves: List<ByteArray>, digestProvider: MerkleTreeHashDigestProvider) =
        MerkleTreeImpl(leaves, digestProvider)

    override fun createProof(
        treeSize: Int,
        leaves: List<IndexedMerkleLeaf>,
        hashes: List<SecureHash>
    ) = MerkleProofImpl(treeSize, leaves, hashes)

    override fun createHashDigestProvider(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: HashMap<String, Any>?,
    ): MerkleTreeHashDigestProvider {
        when (merkleTreeHashDigestProviderName) {
            "DefaultHashDigestProvider" ->
                return DefaultHashDigestProvider(digestAlgorithmName, digestService)
            "NonceHashDigestProvider.Verify" ->
                return NonceHashDigestProvider.Verify(digestAlgorithmName, digestService)
            "NonceHashDigestProvider.SizeOnlyVerify" ->
                return NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithmName, digestService)
            "TweakableHashDigestProvider" -> {
                require(options != null){"TweakableHashDigestProvider needs leafPrefix and nodePrefix options"}
                require(options.containsKey("leafPrefix")){"TweakableHashDigestProvider needs a leafPrefix option"}
                require(options.containsKey("nodePrefix")){"TweakableHashDigestProvider needs a nodePrefix option"}
                val leafPrefix = options["leafPrefix"]
                val nodePrefix = options["nodePrefix"]
                require(leafPrefix is ByteArray){"TweakableHashDigestProvider needs a ByteArray leafPrefix option"}
                require(nodePrefix is ByteArray){"TweakableHashDigestProvider needs a ByteArray nodePrefix option"}
                return TweakableHashDigestProvider(digestAlgorithmName, digestService, leafPrefix, nodePrefix)
            }
            "NonceHashDigestProvider" -> {
                require(options != null){"NonceHashDigestProvider needs an entropy option"}
                require(options.containsKey("entropy")){"NonceHashDigestProvider needs an entropy option"}
                val entropy = options["entropy"]
                require(entropy is ByteArray){"NonceHashDigestProvider needs a ByteArray entropy option"}
                return NonceHashDigestProvider(digestAlgorithmName, digestService, entropy)
            }
            else ->
                throw(IllegalArgumentException("Unknown merkleTreeHashDigestProviderName: $merkleTreeHashDigestProviderName"))
        }
    }
}