package net.corda.crypto.merkle.tests

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.core.toByteArray
import net.corda.crypto.merkle.MerkleTreeImpl
import net.corda.crypto.merkle.NonceHashDigestProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.osgi.test.common.annotation.InjectService

@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class MerkleTreeFactoryTest {

    @InjectService(timeout = 1000)
    lateinit var merkleTreeFactory: MerkleTreeFactory

    private val digestAlgorithm = DigestAlgorithmName.SHA2_256D
    private lateinit var digestService: DigestService
    private lateinit var nonceHashDigestProvider: NonceHashDigestProvider

    @BeforeAll
    fun setup() {
        val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
        digestService = DigestServiceImpl(schemeMetadata, null)
        val secureRandom = schemeMetadata.secureRandom
        nonceHashDigestProvider = NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)
    }

    @Test
    fun `can inject and use the service`() {
        // val schemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
        // digestService = DigestServiceImpl(schemeMetadata, null)
        // val secureRandom = schemeMetadata.secureRandom
        // nonceHashDigestProvider = NonceHashDigestProvider(digestAlgorithm, digestService, secureRandom)

        val leafData = (0 until 8).map { it.toByteArray() }
        val merkleTreeDirect = MerkleTreeImpl.createMerkleTree(leafData, nonceHashDigestProvider)
        val merkleTreeFromService = merkleTreeFactory.createTree(leafData, nonceHashDigestProvider)

        assertEquals(merkleTreeDirect.root, merkleTreeFromService.root)
    }
}
