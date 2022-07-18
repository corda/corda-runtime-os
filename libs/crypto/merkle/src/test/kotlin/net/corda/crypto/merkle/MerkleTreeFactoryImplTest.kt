package net.corda.crypto.merkle

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProviderName
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProviderOption
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
                MerkleTreeHashDigestProviderName.DEFAULT,
                digestAlgorithm) is DefaultHashDigestProvider
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                MerkleTreeHashDigestProviderName.NONCE_VERIFY,
                digestAlgorithm) is NonceHashDigestProvider.Verify
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                MerkleTreeHashDigestProviderName.NONCE_SIZE_ONLY_VERIFY,
                digestAlgorithm) is NonceHashDigestProvider.SizeOnlyVerify
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf(
                    MerkleTreeHashDigestProviderOption.LEAF_PREFIX to 0.toByteArray(),
                    MerkleTreeHashDigestProviderOption.NODE_PRFIX to 1.toByteArray())
            ) is TweakableHashDigestProvider
        )
        assertTrue(
            merkleTreeFactory.createHashDigestProvider(
                MerkleTreeHashDigestProviderName.NONCE,
                digestAlgorithm, hashMapOf(MerkleTreeHashDigestProviderOption.ENTROPY to 123.toByteArray())
            ) is NonceHashDigestProvider
        )

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf(MerkleTreeHashDigestProviderOption.LEAF_PREFIX to 0.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf(MerkleTreeHashDigestProviderOption.NODE_PRFIX to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf(
                    MerkleTreeHashDigestProviderOption.LEAF_PREFIX to 0,
                    MerkleTreeHashDigestProviderOption.NODE_PRFIX to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.TWEAKABLE,
                digestAlgorithm, hashMapOf(
                    MerkleTreeHashDigestProviderOption.LEAF_PREFIX to 0.toByteArray(),
                    MerkleTreeHashDigestProviderOption.NODE_PRFIX to "1")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.NONCE,
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.NONCE,
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName.NONCE,
                digestAlgorithm, hashMapOf(MerkleTreeHashDigestProviderOption.ENTROPY to 1)
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            merkleTreeFactory.createHashDigestProvider(MerkleTreeHashDigestProviderName("NotexistingProvider"),
                digestAlgorithm
            )
        }
    }
}
