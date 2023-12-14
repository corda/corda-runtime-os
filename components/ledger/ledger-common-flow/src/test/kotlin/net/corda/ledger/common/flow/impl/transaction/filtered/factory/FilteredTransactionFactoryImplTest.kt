package net.corda.ledger.common.flow.impl.transaction.filtered.factory

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters.AuditProof.AuditProofPredicate
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FilteredTransactionFactoryImplTest {

    private companion object {
        val COMPONENT_1 = "Component 1".toByteArray()
        val COMPONENT_2 = "Component 2".toByteArray()
        val COMPONENT_3 = "Component 3".toByteArray()
    }

    private val digestService =
        DigestServiceImpl(PlatformDigestServiceImpl(CipherSchemeMetadataImpl()), null)
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val jsonValidator = JsonValidatorImpl()
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService = mock<SerializationService>()

    private lateinit var wireTransaction: WireTransaction
    private lateinit var filteredTransaction: FilteredTransaction

    private val filteredTransactionFactory = FilteredTransactionFactoryImpl(
        jsonMarshallingService,
        merkleTreeProvider,
        serializationService
    )

    @BeforeEach
    fun beforeEach() {
        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_3, Any::class.java)).thenReturn(MyClassC())
    }

    @Test
    fun `cannot filter over the same component group ordinals multiple times`() {
        wireTransaction = wireTransaction(listOf(listOf(COMPONENT_1, COMPONENT_2)))

        assertThatThrownBy {
            filteredTransaction = filteredTransactionFactory.create(
                wireTransaction,
                componentGroupFilterParameters = listOf(
                    ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { true }),
                    ComponentGroupFilterParameters.AuditProof(1, Any::class.java, AuditProofPredicate.Content { true }),
                    ComponentGroupFilterParameters.AuditProof(1, Any::class.java, AuditProofPredicate.Content { true }),
                )
            )
        }.hasMessageContaining("Unique component group indexes are required when filtering a transaction")
    }

    @Test
    fun `transaction metadata is not filtered out`() {
        wireTransaction = wireTransaction(
            listOf(
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { false }),
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java, AuditProofPredicate.Content { false }),
            )
        )

        assertThat(filteredTransaction.getComponentGroupContent(0)?.single()?.second)
            .isEqualTo(wireTransaction.componentGroupLists.first().single())
        verify(serializationService, never()).deserialize(any<ByteArray>(), eq(TransactionMetadataImpl::class.java))
    }

    @Test
    fun `component groups not included in the filter parameters are filtered out`() {
        wireTransaction = wireTransaction(
            listOf(
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { true }),
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java, AuditProofPredicate.Content { true }),
            )
        )

        assertThat(filteredTransaction.filteredComponentGroups).hasSize(2)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.componentGroupIndex).isEqualTo(0)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.componentGroupIndex).isEqualTo(1)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.merkleProof.proofType).isEqualTo(MerkleProofType.AUDIT)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.proofType).isEqualTo(MerkleProofType.AUDIT)
    }

    @Test
    fun `creates an audit proof from the component group components that are filtered out when the filter function returns false`() {
        wireTransaction = wireTransaction(
            listOf(
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3),
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    0,
                    TransactionMetadata::class.java,
                    AuditProofPredicate.Content { true }
                ),
                ComponentGroupFilterParameters.AuditProof(
                    1,
                    Any::class.java,
                    AuditProofPredicate.Content { it is MyClassA || it is MyClassB }
                ),
            )
        )

        assertThat(filteredTransaction.filteredComponentGroups).hasSize(2)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.componentGroupIndex).isEqualTo(0)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.componentGroupIndex).isEqualTo(1)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.proofType).isEqualTo(MerkleProofType.AUDIT)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.leaves).hasSize(2)
    }

    @Test
    fun `creates a size proof instead of an audit proof when the component group contains no components after applying filtering`() {
        wireTransaction = wireTransaction(
            listOf(
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3),
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        val componentGroupMerkleTreeDigestProvider1 = wireTransaction.getComponentGroupMerkleTreeDigestProvider(
            wireTransaction.privacySalt,
            1
        )
        val componentGroupMerkleTreeSizeProofProvider1 =
            checkNotNull(componentGroupMerkleTreeDigestProvider1 as? MerkleTreeHashDigestProviderWithSizeProofSupport) {
                "Expected to have digest provider with size proof support"
            }

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { true }),
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java, AuditProofPredicate.Content { false }),
            )
        )

        assertThat(filteredTransaction.filteredComponentGroups).hasSize(2)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.componentGroupIndex).isEqualTo(0)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.componentGroupIndex).isEqualTo(1)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.proofType).isEqualTo(MerkleProofType.SIZE)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof).isEqualTo(
            componentGroupMerkleTreeSizeProofProvider1.getSizeProof(wireTransaction.componentMerkleTrees[1]!!.leaves)
        )
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.leaves).hasSize(3)
    }

    @Test
    fun `creates an audit proof containing a default value instead of a size proof when the component group contains no components`() {
        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_3, Any::class.java)).thenReturn(MyClassC())

        wireTransaction = wireTransaction(
            listOf(
                emptyList(),
                listOf(COMPONENT_1, COMPONENT_2),
                emptyList()
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { true }),
                ComponentGroupFilterParameters.SizeProof(1),
            )
        )

        assertThat(filteredTransaction.filteredComponentGroups).hasSize(2)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.componentGroupIndex).isEqualTo(0)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.componentGroupIndex).isEqualTo(1)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.proofType).isEqualTo(MerkleProofType.AUDIT)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof).isEqualTo(
            wireTransaction.componentMerkleTrees[1]!!.createAuditProof(
                listOf(0)
            )
        )
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.leaves).hasSize(1)
    }

    @Test
    fun `create a size proof when the component group contains components`() {
        wireTransaction = wireTransaction(
            listOf(
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3),
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadata::class.java, AuditProofPredicate.Content { true }),
                ComponentGroupFilterParameters.SizeProof(1),
            )
        )

        assertThat(filteredTransaction.filteredComponentGroups).hasSize(2)
        assertThat(filteredTransaction.filteredComponentGroups[0]!!.componentGroupIndex).isEqualTo(0)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.componentGroupIndex).isEqualTo(1)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.proofType).isEqualTo(MerkleProofType.SIZE)
        assertThat(filteredTransaction.filteredComponentGroups[1]!!.merkleProof.leaves).hasSize(3)
    }

    private fun wireTransaction(componentGroupLists: List<List<ByteArray>>): WireTransaction {
        return getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = componentGroupLists
        )
    }

    @CordaSerializable
    private class MyClassA

    @CordaSerializable
    private class MyClassB

    @CordaSerializable
    private class MyClassC
}
