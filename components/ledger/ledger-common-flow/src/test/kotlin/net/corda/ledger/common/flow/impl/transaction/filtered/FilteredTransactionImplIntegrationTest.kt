package net.corda.ledger.common.flow.impl.transaction.filtered

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.cipher.suite.impl.PlatformDigestServiceImpl
import net.corda.common.json.validation.impl.JsonValidatorImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Has no benefit being an OSGi test and cannot be moved without causing issues due to mocks.
 */
@Suppress("MaxLineLength")
class FilteredTransactionImplIntegrationTest {

    private companion object {
        val COMPONENT_1 = "Component 1".toByteArray()
        val COMPONENT_2 = "Component 2".toByteArray()
        val COMPONENT_3 = "Component 3".toByteArray()
        val COMPONENT_4 = "Component 4".toByteArray()
        val COMPONENT_5 = "Component 5".toByteArray()
        val COMPONENT_6 = "Component 6".toByteArray()
        val COMPONENT_10 = "Component 10".toByteArray()
    }

    private lateinit var wireTransaction: WireTransaction
    private lateinit var filteredTransaction: FilteredTransaction

    private val digestService =
        DigestServiceImpl(PlatformDigestServiceImpl(CipherSchemeMetadataImpl()), null)
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val jsonValidator = JsonValidatorImpl()
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService = mock<SerializationService>()

    private val filteredTransactionFactory = FilteredTransactionFactoryImpl(
        jsonMarshallingService,
        merkleTreeProvider,
        serializationService
    )

    @BeforeEach
    fun beforeEach() {
    }

    @Test
    fun `transaction can be filtered and successfully verified`() {

        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_3, Any::class.java)).thenReturn(MyClassC())
        whenever(serializationService.deserialize(COMPONENT_4, Any::class.java)).thenReturn(MyClassC())
        whenever(serializationService.deserialize(COMPONENT_5, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_6, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_10, Any::class.java)).thenReturn(MyClassC())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3, COMPONENT_4),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3, COMPONENT_4, COMPONENT_5),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3, COMPONENT_4, COMPONENT_5, COMPONENT_6),
                listOf(COMPONENT_1),
                listOf(COMPONENT_1),
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3, COMPONENT_4, COMPONENT_5, COMPONENT_6, COMPONENT_10)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadataImpl::class.java) { true },
                ComponentGroupFilterParameters.SizeProof(1),
                ComponentGroupFilterParameters.AuditProof(2, Any::class.java) { it is MyClassC },
                ComponentGroupFilterParameters.AuditProof(3, Any::class.java) { it is MyClassC },
                ComponentGroupFilterParameters.SizeProof(4),
                ComponentGroupFilterParameters.AuditProof(5, Any::class.java) { it is MyClassC },
                ComponentGroupFilterParameters.AuditProof(6, Any::class.java) { it is MyClassC },
                ComponentGroupFilterParameters.SizeProof(10),
            )
        )

        filteredTransaction.verify()
    }

    @Test
    fun `component group content can be retrieved from a filtered transaction when the merkle proof for the group is an audit proof`() {

        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadataImpl::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(2, Any::class.java) { true },
            )
        )

        val componentGroup1 = filteredTransaction.getComponentGroupContent(1)!!
        val componentGroup2 = filteredTransaction.getComponentGroupContent(2)!!

        assertEquals(1, componentGroup1.size)
        assertEquals(2, componentGroup2.size)
        assertEquals(0, componentGroup1.single().first)
        assertEquals(0, componentGroup2.first().first)
        assertEquals(1, componentGroup2[1].first)
        assertArrayEquals(COMPONENT_1, componentGroup1.single().second)
        assertArrayEquals(COMPONENT_1, componentGroup2.first().second)
        assertArrayEquals(COMPONENT_2, componentGroup2[1].second)
    }

    @Test
    fun `retrieved component group content does not included filtered content when the merkle proof for the group is an audit proof`() {

        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_3, Any::class.java)).thenReturn(MyClassC())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadataImpl::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java) { it is MyClassA || it is MyClassC },
                ComponentGroupFilterParameters.AuditProof(2, Any::class.java) { it is MyClassA || it is MyClassC },
            )
        )

        val componentGroup1 = filteredTransaction.getComponentGroupContent(1)!!
        val componentGroup2 = filteredTransaction.getComponentGroupContent(2)!!

        assertEquals(1, componentGroup1.size)
        assertEquals(2, componentGroup2.size)
        assertEquals(0, componentGroup1.single().first)
        assertEquals(0, componentGroup2.first().first)
        assertEquals(2, componentGroup2[1].first)
        assertArrayEquals(COMPONENT_1, componentGroup1.single().second)
        assertArrayEquals(COMPONENT_1, componentGroup2.first().second)
        assertArrayEquals(COMPONENT_3, componentGroup2[1].second)
    }

    @Test
    fun `cannot retrieve filtered out component group content when the merkle proof for the group is an audit proof`() {

        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())
        whenever(serializationService.deserialize(COMPONENT_3, Any::class.java)).thenReturn(MyClassC())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2, COMPONENT_3)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadataImpl::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(1, Any::class.java) { it is MyClassA || it is MyClassC },
            )
        )

        val componentGroup1 = filteredTransaction.getComponentGroupContent(1)!!
        val componentGroup2 = filteredTransaction.getComponentGroupContent(2)

        assertEquals(1, componentGroup1.size)
        assertNull(componentGroup2)
        assertArrayEquals(COMPONENT_1, componentGroup1.single().second)
    }

    @Test
    fun `original component group content cannot be retrieved from a filtered transaction when the merkle proof for the group is a size proof`() {

        whenever(serializationService.deserialize(COMPONENT_1, Any::class.java)).thenReturn(MyClassA())
        whenever(serializationService.deserialize(COMPONENT_2, Any::class.java)).thenReturn(MyClassB())

        wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService,
            jsonValidator,
            componentGroupLists = listOf(
                listOf(COMPONENT_1),
                listOf(COMPONENT_1, COMPONENT_2)
            )
        )

        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(0, TransactionMetadataImpl::class.java) { true },
                ComponentGroupFilterParameters.SizeProof(1),
                ComponentGroupFilterParameters.SizeProof(2),
            )
        )

        val componentGroup1 = filteredTransaction.getComponentGroupContent(1)!!
        val componentGroup2 = filteredTransaction.getComponentGroupContent(2)!!

        assertEquals(1, componentGroup1.size)
        assertEquals(2, componentGroup2.size)
        assertNotEquals(COMPONENT_1.toList(), componentGroup1.single().toList())
        assertNotEquals(COMPONENT_1.toList(), componentGroup2.first().toList())
        assertNotEquals(COMPONENT_2.toList(), componentGroup2[1].toList())
    }

    @CordaSerializable
    private class MyClassA

    @CordaSerializable
    private class MyClassB

    @CordaSerializable
    private class MyClassC
}