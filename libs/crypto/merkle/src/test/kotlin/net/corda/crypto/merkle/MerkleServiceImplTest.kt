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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        assertIs<DefaultHashDigestProvider>(
            merkleService.createHashDigestProvider(
                "DefaultHashDigestProvider",
                digestAlgorithm)
        )
        assertIs<NonceHashDigestProvider.Verify>(
            merkleService.createHashDigestProvider(
                "NonceHashDigestProvider.Verify",
                digestAlgorithm)
        )
        assertIs<NonceHashDigestProvider.SizeOnlyVerify>(
            merkleService.createHashDigestProvider(
                "NonceHashDigestProvider.SizeOnlyVerify",
                digestAlgorithm)
        )
        assertIs<TweakableHashDigestProvider>(
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray(), "nodePrefix" to 1.toByteArray())
            )
        )
        assertIs<NonceHashDigestProvider>(
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf("entropy" to 123.toByteArray())
            )
        )

        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf()
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray())
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("nodePrefix" to 1.toByteArray())
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0, "nodePrefix" to 1.toByteArray())
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("TweakableHashDigestProvider",
                digestAlgorithm, hashMapOf("leafPrefix" to 0.toByteArray(), "nodePrefix" to "1")
            )
        }

        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf()
            )
        }
        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("NonceHashDigestProvider",
                digestAlgorithm, hashMapOf("entropy" to 1)
            )
        }

        assertFailsWith(IllegalArgumentException::class){
            merkleService.createHashDigestProvider("NotexistingProvider",
                digestAlgorithm
            )
        }
    }
}