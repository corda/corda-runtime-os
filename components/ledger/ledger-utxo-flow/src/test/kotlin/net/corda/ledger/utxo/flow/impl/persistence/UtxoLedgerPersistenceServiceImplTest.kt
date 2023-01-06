package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.flow.impl.persistence.external.events.AbstractUtxoLedgerExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class UtxoLedgerPersistenceServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytes<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val utxoSignedTransactionFactory = mock<UtxoSignedTransactionFactory>()

    private lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractUtxoLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        utxoLedgerPersistenceService = UtxoLedgerPersistenceServiceImpl(
            externalEventExecutor,
            serializationService,
            utxoSignedTransactionFactory
        )

        whenever(serializationService.serialize(any())).thenReturn(serializedBytes)
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))
    }

    @Test
    fun `persist executes successfully`() {
        val expectedObj = mock<CordaPackageSummaryImpl>()
        whenever(serializationService.deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())).thenReturn(expectedObj)
        val transaction = mock<UtxoSignedTransactionInternal>()
        whenever(transaction.wireTransaction).thenReturn(mock())
        whenever(transaction.signatures).thenReturn(mock())

        assertThat(
            utxoLedgerPersistenceService.persist(
                transaction,
                TransactionStatus.VERIFIED
            )
        ).isEqualTo(listOf(expectedObj))

        verify(serializationService).serialize(any())
        verify(serializationService).deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `persistIfDoesNotExist returns a status of DOES_NOT_EXIST when null is returned from the external event factory`() {
        persistIfDoesNotExist(returnedStatus = null) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    TransactionStatus.VERIFIED
                )
            ).isEqualTo(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(packageSummary))
        }
    }

    @Test
    fun `persistIfDoesNotExist returns a status of UNVERIFIED when UNVERIFIED is returned from the external event factory`() {
        persistIfDoesNotExist(TransactionStatus.UNVERIFIED.value) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    TransactionStatus.VERIFIED
                )
            ).isEqualTo(TransactionExistenceStatus.UNVERIFIED to listOf(packageSummary))
        }
    }

    @Test
    fun `persistIfDoesNotExist returns a status of VERIFIED when VERIFIED is returned from the external event factory`() {
        persistIfDoesNotExist(TransactionStatus.VERIFIED.value) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    TransactionStatus.VERIFIED
                )
            ).isEqualTo(TransactionExistenceStatus.VERIFIED to listOf(packageSummary))
        }
    }

    @Test
    fun `persistIfDoesNotExist throws an exception when an invalid status is returned from the external event factory`() {
        persistIfDoesNotExist("Invalid") { transaction, _ ->
            assertThatThrownBy {
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    TransactionStatus.VERIFIED
                )
            }.isExactlyInstanceOf(IllegalStateException::class.java)
        }
    }

    @Test
    fun `find executes successfully`() {
        val wireTransaction = mock<WireTransaction>()
        whenever(wireTransaction.componentGroupLists).thenReturn(List(UtxoComponentGroup.values().size) { listOf() })

        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val expectedObj = UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            mock<UtxoLedgerTransactionFactory>(),
            wireTransaction,
            signatures
        )
        val testId = SecureHash.parse("SHA256:1234567890123456")

        whenever(serializationService.deserialize<SignedTransactionContainer>(any<ByteArray>(), any()))
            .thenReturn(SignedTransactionContainer(wireTransaction, signatures))

        whenever(utxoSignedTransactionFactory.create(any<WireTransaction>(), any(), any())).thenReturn(expectedObj)

        assertThat(utxoLedgerPersistenceService.find(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<UtxoSignedTransactionInternal>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }

    private fun persistIfDoesNotExist(
        returnedStatus: String?,
        test: (transaction: UtxoSignedTransaction, packageSummary: CordaPackageSummary) -> Unit
    ) {
        val packageSummary = mock<CordaPackageSummaryImpl>()
        whenever(
            serializationService.deserialize<Pair<String?, List<CordaPackageSummary>>>(
                any<ByteArray>(),
                any()
            )
        ).thenReturn(returnedStatus to listOf(packageSummary))
        val transaction = mock<UtxoSignedTransactionInternal>()
        whenever(transaction.wireTransaction).thenReturn(mock())
        whenever(transaction.signatures).thenReturn(mock())

        test(transaction, packageSummary)

        verify(serializationService).serialize(any())
        verify(serializationService).deserialize<Pair<String?, List<CordaPackageSummary>>>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionIfDoesNotExistExternalEventFactory::class.java)
    }
}
