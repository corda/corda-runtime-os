package net.corda.crypto.merkle.impl.tests

import net.corda.crypto.core.toByteArray
import net.corda.v5.application.crypto.MerkleTreeFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_NONCE_NAME
import org.junit.jupiter.api.Assertions.assertTrue
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
    private lateinit var nonceHashDigestProvider: MerkleTreeHashDigestProvider

    @BeforeAll
    fun setup() {
        nonceHashDigestProvider = merkleTreeFactory.createHashDigest(
            HASH_DIGEST_PROVIDER_NONCE_NAME,
            digestAlgorithm,
            mapOf(HASH_DIGEST_PROVIDER_ENTROPY_OPTION to "1".repeat(32).toByteArray())
        ) as MerkleTreeHashDigestProvider
    }

    @Test
    fun `can inject and use the service`() {
        val leafData = (0 until 8).map { it.toByteArray() }
        val merkleTree = merkleTreeFactory.createTree(leafData, nonceHashDigestProvider)

        val merkleProof = merkleTree.createAuditProof(listOf(1,3,5))

        assertTrue(merkleProof.verify(merkleTree.root, nonceHashDigestProvider))
    }
}
