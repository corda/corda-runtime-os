package net.corda.crypto.merkle

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows

class MerkleServiceImplTest {
    companion object {
        private val digestAlgorithm = DigestAlgorithmName.SHA2_256D

        private lateinit var digestService: DigestService
        private lateinit var nonceHashDigestProvider: NonceHashDigestProvider
        private lateinit var merkleService: MerkleService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            val secureRandom = schemeMetadata.secureRandom

            nonceHashDigestProvider = NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)

            merkleService = MerkleServiceImpl(digestService)
        }
    }

    @Test
    fun createTree() {
        val leafData = (0 until 8).map { it.toByteArray() }
        val merkleTreeDirect = MerkleTreeImpl.createMerkleTree(leafData, nonceHashDigestProvider)
        val merkleTreeFromService = merkleService.createTree(leafData, nonceHashDigestProvider)

        assertEquals(merkleTreeDirect.root, merkleTreeFromService.root)
    }

    @Test
    fun createProof() {
        val leafData = (0 until 8).map { it.toByteArray() }
        var indices = listOf(1,3,5)

        val merkleTreeDirect = MerkleTreeImpl.createMerkleTree(leafData, nonceHashDigestProvider)
        val proofDirect = merkleTreeDirect.createAuditProof(indices)

        val merkleTreeFromService = merkleService.createTree(leafData, nonceHashDigestProvider)
        val proofFromService = merkleTreeFromService.createAuditProof(indices)

        assertEquals(proofDirect, proofFromService)
        assertTrue(proofDirect.verify(merkleTreeFromService.root, nonceHashDigestProvider))
        assertTrue(proofFromService.verify(merkleTreeDirect.root, nonceHashDigestProvider))
    }

    @Test
    fun createHashDigestProvider() {
        assertTrue(
            merkleService.createHashDigestProvider(
                "DefaultHashDigestProvider",
                digestAlgorithm) is DefaultHashDigestProvider)
        assertTrue(
            merkleService.createHashDigestProvider(
                "DefaultHashDigestProvider",
                digestAlgorithm) is DefaultHashDigestProvider
        )
        assertTrue(
            merkleService.createHashDigestProvider(
                "NonceHashDigestProvider.Verify",
                digestAlgorithm) is NonceHashDigestProvider.Verify
        )
        assertTrue(
            merkleService.createHashDigestProvider(
                "NonceHashDigestProvider.SizeOnlyVerify",
                digestAlgorithm) is NonceHashDigestProvider.SizeOnlyVerify
        )
        assertTrue(
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray(), "nodePrefix" to 1.toByteArray())
            ) is TweakableHashDigestProvider
        )
        assertTrue(
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf("entropy" to 123.toByteArray())
            ) is NonceHashDigestProvider
        )

        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("nodePrefix" to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0, "nodePrefix" to 1.toByteArray())
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray(), "nodePrefix" to "1")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf("entropy" to 1)
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            merkleService.createHashDigestProvider("NotexistingProvider",
                digestAlgorithm
            )
        }
    }
}
