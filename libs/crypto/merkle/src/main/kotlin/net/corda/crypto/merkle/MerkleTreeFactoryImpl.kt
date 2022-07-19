package net.corda.crypto.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
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
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<String, Any>,
    ): MerkleTreeHashDigestProvider {
        when (merkleTreeHashDigestProviderName) {
            HASH_DIGEST_PROVIDER_DEFAULT_NAME ->
                return DefaultHashDigestProvider(digestAlgorithmName, digestService)
            HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME ->
                return NonceHashDigestProvider.Verify(digestAlgorithmName, digestService)
            HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME ->
                return NonceHashDigestProvider.SizeOnlyVerify(digestAlgorithmName, digestService)
            HASH_DIGEST_PROVIDER_TWEAKABLE_NAME -> {
                require(options.containsKey(HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION)){
                    "TweakableHashDigestProvider needs a $HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION option"
                }
                require(options.containsKey(HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION)){
                    "TweakableHashDigestProvider needs a $HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION option"
                }
                val leafPrefix = options[HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION]
                val nodePrefix = options[HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION]
                require(leafPrefix is ByteArray){
                    "TweakableHashDigestProvider needs a ByteArray $HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION option"
                }
                require(nodePrefix is ByteArray){
                    "TweakableHashDigestProvider needs a ByteArray $HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION option"
                }
                return TweakableHashDigestProvider(digestAlgorithmName, digestService, leafPrefix, nodePrefix)
            }
            HASH_DIGEST_PROVIDER_NONCE_NAME -> {
                require(options.containsKey(HASH_DIGEST_PROVIDER_ENTROPY_OPTION)){
                    "NonceHashDigestProvider needs an $HASH_DIGEST_PROVIDER_ENTROPY_OPTION option"
                }
                val entropy = options[HASH_DIGEST_PROVIDER_ENTROPY_OPTION]
                require(entropy is ByteArray){
                    "NonceHashDigestProvider needs a ByteArray $HASH_DIGEST_PROVIDER_ENTROPY_OPTION option"
                }
                return NonceHashDigestProvider(digestAlgorithmName, digestService, entropy)
            }
            else ->
                throw(IllegalArgumentException("Unknown merkleTreeHashDigestProviderName: $merkleTreeHashDigestProviderName"))
        }
    }
}