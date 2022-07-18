package net.corda.crypto.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProviderName
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProviderOption
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [MerkleTreeFactory::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class MerkleTreeFactoryImpl @Activate constructor(
    @Reference(service = DigestService::class)
    private val digestService: DigestService
) : MerkleTreeFactory, SingletonSerializeAsToken {
    override fun createTree(leaves: List<ByteArray>, digestProvider: MerkleTreeHashDigestProvider) =
        MerkleTreeImpl(leaves, digestProvider)

    override fun createHashDigestProvider(
        merkleTreeHashDigestProviderName: MerkleTreeHashDigestProviderName,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<MerkleTreeHashDigestProviderOption, Any>,
    ): MerkleTreeHashDigestProvider {
        when (merkleTreeHashDigestProviderName) {
            MerkleTreeHashDigestProviderName.DEFAULT ->
                return DefaultHashDigestProvider(digestAlgorithmName, digestService)
            MerkleTreeHashDigestProviderName.NONCE_VERIFY ->
                return NonceHashDigestProvider.Verify(digestAlgorithmName, digestService)
            MerkleTreeHashDigestProviderName.NONCE_SIZE_ONLY_VERIFY ->
                return NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithmName, digestService)
            MerkleTreeHashDigestProviderName.TWEAKABLE -> {
                require(options.containsKey(MerkleTreeHashDigestProviderOption.LEAF_PREFIX)){"TweakableHashDigestProvider needs a leafPrefix option"}
                require(options.containsKey(MerkleTreeHashDigestProviderOption.NODE_PRFIX)){"TweakableHashDigestProvider needs a nodePrefix option"}
                val leafPrefix = options[MerkleTreeHashDigestProviderOption.LEAF_PREFIX]
                val nodePrefix = options[MerkleTreeHashDigestProviderOption.NODE_PRFIX]
                require(leafPrefix is ByteArray){"TweakableHashDigestProvider needs a ByteArray leafPrefix option"}
                require(nodePrefix is ByteArray){"TweakableHashDigestProvider needs a ByteArray nodePrefix option"}
                return TweakableHashDigestProvider(digestAlgorithmName, digestService, leafPrefix, nodePrefix)
            }
            MerkleTreeHashDigestProviderName.NONCE -> {
                require(options.containsKey(MerkleTreeHashDigestProviderOption.ENTROPY)){"NonceHashDigestProvider needs an entropy option"}
                val entropy = options[MerkleTreeHashDigestProviderOption.ENTROPY]
                require(entropy is ByteArray){"NonceHashDigestProvider needs a ByteArray entropy option"}
                return NonceHashDigestProvider(digestAlgorithmName, digestService, entropy)
            }
            else ->
                throw(IllegalArgumentException("Unknown merkleTreeHashDigestProviderName: $merkleTreeHashDigestProviderName"))
        }
    }
}