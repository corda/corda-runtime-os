package net.corda.ledger.common.flow.impl.transaction.filtered

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.crypto.merkle.impl.NonceHashDigestProvider
import net.corda.ledger.common.data.transaction.COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.flow.transaction.filtered.FilteredComponentGroup
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransactionVerificationException
import net.corda.ledger.common.flow.transaction.filtered.MerkleProofType
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

class FilteredTransactionImplTest {

    private companion object {
        val digestAlgorithmName = DigestAlgorithmName.SHA2_256D.name
        const val metadataJson = "{}"
        val metadata = TransactionMetadata(
            linkedMapOf(
                TransactionMetadata.DIGEST_SETTINGS_KEY to linkedMapOf<String, Any>(
                    ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to "algorithm",
                    ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY to Base64.getEncoder()
                        .encodeToString("leaf prefix".toByteArray(Charsets.UTF_8)),
                    ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY to Base64.getEncoder()
                        .encodeToString("node prefix".toByteArray(Charsets.UTF_8)),
                    COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY to digestAlgorithmName
                ),
            )
        )
    }

    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = DigestServiceImpl(cipherSchemeMetadata, null)

    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val jsonMarshallingService = mock<JsonMarshallingService>()

    private val merkleTreeHashDigestCaptor = argumentCaptor<MerkleTreeHashDigest>()

    private val componentGroupMerkleProof = mock<MerkleProof>()
    private val filteredComponentGroup0Proof = mock<MerkleProof>()
    private val filteredComponentGroup1Proof = mock<MerkleProof>()
    private val filteredComponentGroup0 = FilteredComponentGroup(0, filteredComponentGroup0Proof, MerkleProofType.AUDIT)
    private val filteredComponentGroup1WithAuditProof = FilteredComponentGroup(1, filteredComponentGroup1Proof, MerkleProofType.AUDIT)
    private val filteredComponentGroup1WithSizeProof = FilteredComponentGroup(1, filteredComponentGroup1Proof, MerkleProofType.SIZE)
    private val indexedMerkleLeaf0 = indexedMerkleLeaf(0, byteArrayOf(1))
    private val indexedMerkleLeaf1 = indexedMerkleLeaf(1, byteArrayOf(2))
    private val indexedMerkleLeaf2 = indexedMerkleLeaf(2, byteArrayOf(3))

    private lateinit var filteredTransaction: FilteredTransaction

    @BeforeEach
    fun beforeEach() {
        whenever(jsonMarshallingService.parse(metadataJson, TransactionMetadata::class.java)).thenReturn(metadata)
    }

    @Test
    fun `verification fails when there are no component group merkle proof leaves`() {
        val componentGroupMerkleProof = mock<MerkleProof>()

        filteredTransaction = filteredTransaction(filteredComponentGroups = emptyMap())

        whenever(componentGroupMerkleProof.leaves).thenReturn(emptyList())

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("At least one component group merkle leaf is required")
    }

    @Test
    fun `verification fails when there is no metadata leaf`() {
        filteredTransaction = filteredTransaction(filteredComponentGroups = emptyMap())

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(IndexedMerkleLeaf(1, byteArrayOf(), byteArrayOf())))

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Top level Merkle proof does not contain a leaf with index 0")
    }

    @Test
    fun `verification fails when there is more than one metadata leaf`() {
        filteredTransaction = filteredTransaction(filteredComponentGroups = emptyMap())

        whenever(componentGroupMerkleProof.leaves)
            .thenReturn(
                listOf(
                    indexedMerkleLeaf(0),
                    indexedMerkleLeaf(0)
                )
            )

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Top level Merkle proof contains more than one leaf with index 0")
    }

    @Test
    fun `verification fails when the component group merkle proof leaf indexes do not match the filtered component group indexes`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves)
            .thenReturn(
                listOf(
                    indexedMerkleLeaf(0),
                    indexedMerkleLeaf(1),
                    indexedMerkleLeaf(2)
                )
            )

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Top level Merkle proof does not contain the same indexes as the filtered component groups")
    }

    @Test
    fun `verification fails when filtered component group 0's tree size is 1`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1)))

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(1)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have a tree size of 2 but has a size of 1")
    }

    @Test
    fun `verification fails when filtered component group 0's tree size is greater than 2`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1)))

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(5)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have a tree size of 2 but has a size of 5")
    }

    @Test
    fun `verification fails when filtered component group 0's has no leaves`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1)))

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(1)
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(emptyList())

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have a tree size of 2 but has a size of 1")
    }

    @Test
    fun `verification fails when filtered component group 0's number of leaves is greater than 2`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1)))

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(2)
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(
            listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1), indexedMerkleLeaf(2))
        )

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have two leaves but contains 3")
    }

    @Test
    fun `verification fails when the transaction's id does not verify with the component group merkle proof`() {
        val transactionId = SecureHash("SHA", byteArrayOf(1, 2, 3))
        filteredTransaction = filteredTransaction(
            transactionId,
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        whenever(componentGroupMerkleProof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1)))
        whenever(componentGroupMerkleProof.verify(eq(transactionId), any())).thenReturn(false)

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(2)
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(
            listOf(
                indexedMerkleLeaf(0),
                indexedMerkleLeaf(1, metadataJson.encodeToByteArray())
            )
        )

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining("Top level Merkle proof cannot be verified against transaction's id")
    }

    @Test
    fun `verification succeeds when there are no filtered component groups other than the metadata`() {
        filteredTransaction = filteredTransaction(filteredComponentGroups = mapOf(0 to filteredComponentGroup0))

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf(0)))

        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(2)
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(
            listOf(
                indexedMerkleLeaf(0),
                indexedMerkleLeaf(1, metadataJson.encodeToByteArray())
            )
        )

        assertDoesNotThrow { filteredTransaction.verify() }
    }

    @Test
    fun `verification succeeds when each component group successfully verifies with audit proofs`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(true)

        assertDoesNotThrow { filteredTransaction.verify() }
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.firstValue)
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.secondValue)
    }

    @Test
    fun `verification succeeds when each component group successfully verifies with a size proof containing more than one leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(5)
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(true)

        assertDoesNotThrow { filteredTransaction.verify() }
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.firstValue)
        assertInstanceOf(NonceHashDigestProvider.SizeOnlyVerify::class.java, merkleTreeHashDigestCaptor.secondValue)
    }

    @Test
    fun `verification succeeds when each component group successfully verifies with a size proof containing a single non-empty leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(1)
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(listOf(indexedMerkleLeaf(0, byteArrayOf(1))))
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(true)

        assertDoesNotThrow { filteredTransaction.verify() }
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.firstValue)
        assertInstanceOf(NonceHashDigestProvider.SizeOnlyVerify::class.java, merkleTreeHashDigestCaptor.secondValue)
    }

    @Test
    fun `verification succeeds when each component group successfully verifies with a size proof containing a single empty leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(1)
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(listOf(indexedMerkleLeaf(0, byteArrayOf())))
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(true)

        assertDoesNotThrow { filteredTransaction.verify() }
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.firstValue)
        assertInstanceOf(NonceHashDigestProvider.Verify::class.java, merkleTreeHashDigestCaptor.secondValue)
    }

    @Test
    fun `verification fails when a component group fails to verify with an audit proof`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(false)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining(
                "Component group leaf [index = 1] Merkle proof cannot be verified against the top level Merkle tree's leaf with the same " +
                        "index"
            )
    }

    @Test
    fun `verification fails when a component group fails to verify with a size proof containing more than one leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(5)
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(false)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining(
                "Component group leaf [index = 1] Merkle proof cannot be verified against the top level Merkle tree's leaf with the same " +
                        "index"
            )
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
    }

    @Test
    fun `verification fails when a component group fails to verify with a size proof containing a single non-empty leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(1)
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(listOf(indexedMerkleLeaf(0, byteArrayOf(1))))
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(false)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining(
                "Component group leaf [index = 1] Merkle proof cannot be verified against the top level Merkle tree's leaf with the same " +
                        "index"
            )
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
    }

    @Test
    fun `verification fails when a component group fails to verify with a size proof containing a single empty leaf`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithSizeProof
            )
        )

        componentGroupMerkleProofVerifies(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1))

        filteredComponentGroup0ProofVerifies()

        whenever(filteredComponentGroup1Proof.treeSize).thenReturn(1)
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(listOf(indexedMerkleLeaf(0, byteArrayOf())))
        whenever(
            filteredComponentGroup1Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf1.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(false)

        assertThatThrownBy { filteredTransaction.verify() }
            .isInstanceOf(FilteredTransactionVerificationException::class.java)
            .hasMessageContaining(
                "Component group leaf [index = 1] Merkle proof cannot be verified against the top level Merkle tree's leaf with the same " +
                        "index"
            )
        verify(filteredComponentGroup0Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).verify(any(), any())
        verify(filteredComponentGroup1Proof).treeSize
    }

    @Test
    fun `metadata cannot be retrieved if its component group does not exist`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        assertThatThrownBy { filteredTransaction.metadata }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof does not exist")
    }

    @Test
    fun `metadata cannot be retrieved if its component group has more than two leaves`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(listOf(indexedMerkleLeaf0, indexedMerkleLeaf1, indexedMerkleLeaf2))
        assertThatThrownBy { filteredTransaction.metadata }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have two leaves but contains 3")
    }

    @Test
    fun `metadata cannot be retrieved if its component group has no leaves`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(emptyList())
        assertThatThrownBy { filteredTransaction.metadata }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Component group 0's Merkle proof must have two leaves but contains 0")
    }

    @Test
    fun `metadata can be retrieved`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(
            listOf(
                indexedMerkleLeaf(1),
                indexedMerkleLeaf(0, metadataJson.encodeToByteArray())
            )
        )
        assertEquals(metadata, filteredTransaction.metadata)
    }

    @Test
    fun `component group content is null if the group does not exist`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        assertNull(filteredTransaction.getComponentGroupContent(5))
    }

    @Test
    fun `component group content is empty if there are no leaves`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(emptyList())
        assertThat(filteredTransaction.getComponentGroupContent(1)).isEmpty()
    }

    @Test
    fun `component group content can be retrieved`() {
        filteredTransaction = filteredTransaction(
            filteredComponentGroups = mapOf(
                0 to filteredComponentGroup0,
                1 to filteredComponentGroup1WithAuditProof
            )
        )
        val metadataBytes = metadataJson.encodeToByteArray()
        val indexedMerkleLeafs = listOf(
            indexedMerkleLeaf(0),
            indexedMerkleLeaf(1, byteArrayOf(1)),
            indexedMerkleLeaf(2, byteArrayOf(1, 2)),
            indexedMerkleLeaf(3, byteArrayOf(3))
        )
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(listOf(indexedMerkleLeaf(0), indexedMerkleLeaf(1, metadataBytes)))
        whenever(filteredComponentGroup1Proof.leaves).thenReturn(indexedMerkleLeafs)

        assertArrayEquals(metadataBytes, filteredTransaction.getComponentGroupContent(0)!!.single().second)
        filteredTransaction.getComponentGroupContent(1)!!.let {
            assertEquals(indexedMerkleLeafs[1].index - 1, it[0].first)
            assertEquals(indexedMerkleLeafs[2].index - 1, it[1].first)
            assertEquals(indexedMerkleLeafs[3].index - 1, it[2].first)
            assertArrayEquals(indexedMerkleLeafs[1].leafData, it[0].second)
            assertArrayEquals(indexedMerkleLeafs[2].leafData, it[1].second)
            assertArrayEquals(indexedMerkleLeafs[3].leafData, it[2].second)
        }
    }

    private fun componentGroupMerkleProofVerifies(indexedMerkleLeaves: List<IndexedMerkleLeaf>) {
        whenever(componentGroupMerkleProof.leaves).thenReturn(indexedMerkleLeaves)
        whenever(componentGroupMerkleProof.verify(any(), any())).thenReturn(true)
    }

    private fun filteredComponentGroup0ProofVerifies() {
        whenever(filteredComponentGroup0Proof.treeSize).thenReturn(2)
        whenever(filteredComponentGroup0Proof.leaves).thenReturn(
            listOf(
                indexedMerkleLeaf(0),
                indexedMerkleLeaf(1, metadataJson.encodeToByteArray())
            )
        )
        whenever(
            filteredComponentGroup0Proof.verify(
                eq(SecureHash(digestAlgorithmName, indexedMerkleLeaf0.leafData)),
                merkleTreeHashDigestCaptor.capture()
            )
        ).thenReturn(true)
    }

    private fun filteredTransaction(
        transactionId: SecureHash = SecureHash("SHA", byteArrayOf(1, 2, 3)),
        filteredComponentGroups: Map<Int, FilteredComponentGroup>
    ): FilteredTransaction {
        return FilteredTransactionImpl(
            transactionId,
            componentGroupMerkleProof,
            filteredComponentGroups,
            jsonMarshallingService,
            merkleTreeProvider
        )
    }

    private fun indexedMerkleLeaf(index: Int, leafData: ByteArray = byteArrayOf(1, 2, 3)): IndexedMerkleLeaf {
        return IndexedMerkleLeaf(index, nonce = byteArrayOf(), leafData)
    }
}