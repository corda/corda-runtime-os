package net.corda.crypto.merkle.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_DEFAULT_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import java.security.SecureRandom

class MerkleTreeFactoryImplTest {
    companion object {
        private val digestAlgorithm = DigestAlgorithmName.SHA2_256D

        private lateinit var digestService: DigestService
        private lateinit var nonceHashDigestProvider: NonceHashDigestProvider
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var secureRandom: SecureRandom

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            secureRandom = schemeMetadata.secureRandom

            nonceHashDigestProvider = NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)

            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
        }
    }

    @Test
    fun createTree() {
        val leafData = (0 until 8).map { it.toByteArray() }
        val merkleTreeDirect = MerkleTreeImpl.createMerkleTree(leafData, nonceHashDigestProvider)
        val merkleTreeFromFactory = merkleTreeFactory.createTree(leafData, nonceHashDigestProvider)

        assertEquals(merkleTreeDirect.root, merkleTreeFromFactory.root)
    }

    @Test
    fun createDefaultHashDigestProvider() {
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_DEFAULT_NAME,
                digestAlgorithm
            ) is DefaultHashDigestProvider
        )
    }

    @Test
    fun createNonceVerifyHashDigestProvider() {
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME,
                digestAlgorithm
            ) is NonceHashDigestProvider.Verify
        )
    }

    @Test
    fun createNonceSizeOnlyVerifyHashDigestProvider() {
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME,
                digestAlgorithm
            ) is NonceHashDigestProvider.SizeOnlyVerify
        )
    }

    @Test
    fun createTweakableHashDigestProvider() {
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(
                    HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to 0.toByteArray(),
                    HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to 1.toByteArray())
            ) is TweakableHashDigestProvider
        )
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to 0.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(
                    HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to 0,
                    HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(
                    HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to 0.toByteArray(),
                    HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to "1")
            )
        }
    }

    @Test
    fun createNonceHashDigestProvider() {
        val entropy = ByteArray(NonceHashDigestProvider.EXPECTED_ENTROPY_LENGTH)
        secureRandom.nextBytes(entropy)
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to entropy)
            ) is NonceHashDigestProvider
        )

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to 1)
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            val tooShortEntropy = ByteArray(NonceHashDigestProvider.EXPECTED_ENTROPY_LENGTH - 1)
            secureRandom.nextBytes(tooShortEntropy)
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to tooShortEntropy)
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            val tooLongEntropy = ByteArray(NonceHashDigestProvider.EXPECTED_ENTROPY_LENGTH + 1)
            secureRandom.nextBytes(tooLongEntropy)
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to tooLongEntropy)
            )
        }
    }

    @Test
    fun failToCreateNonExistingHashDigestProvider() {
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider("NonExistingProvider",
                digestAlgorithm
            )
        }
    }
}
