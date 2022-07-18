package net.corda.crypto.merkle

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.CipherSchemeMetadata
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class MerkleTreeFactoryImplTest {
    companion object {
        private val digestAlgorithm = DigestAlgorithmName.SHA2_256D

        private lateinit var digestService: DigestService
        private lateinit var nonceHashDigestProvider: NonceHashDigestProvider
        private lateinit var merkleTreeFactory: MerkleTreeFactory

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            val secureRandom = schemeMetadata.secureRandom

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
    fun createHashDigestProvider() {
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_DEFAULT_NAME,
                digestAlgorithm) is DefaultHashDigestProvider
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME,
                digestAlgorithm) is NonceHashDigestProvider.Verify
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME,
                digestAlgorithm) is NonceHashDigestProvider.SizeOnlyVerify
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_TWEAKABLE_NAME,
                digestAlgorithm, hashMapOf(
                    HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to 0.toByteArray(),
                    HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to 1.toByteArray())
            ) is TweakableHashDigestProvider
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to 123.toByteArray())
            ) is NonceHashDigestProvider
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

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(HASH_DIGEST_PROVIDER_NONCE_NAME,
                digestAlgorithm, hashMapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to 1)
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider("NotexistingProvider",
                digestAlgorithm
            )
        }
    }
}
