package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.parseSecureHash
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.filtered.FilteredTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.ledger.utxo.flow.impl.cache.StateAndRefCache
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.flow.impl.persistence.external.events.AbstractUtxoLedgerExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindFilteredTransactionsAndSignaturesExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindSignedLedgerTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.utxo.flow.impl.persistence.external.events.PersistTransactionIfDoesNotExistExternalEventFactory
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant

class UtxoLedgerPersistenceServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytesImpl<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private val transactionSignatureService = mock<TransactionSignatureServiceInternal>()
    private val notarySignatureVerificationService = mock<NotarySignatureVerificationServiceInternal>()
    private val utxoSignedTransactionFactory = mock<UtxoSignedTransactionFactory>()
    private val utxoLedgerTransactionFactory = mock<UtxoLedgerTransactionFactory>()
    private val utxoFilteredTransactionFactory = mock<UtxoFilteredTransactionFactory>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val stateAndRefCache = mock<StateAndRefCache>()

    private val notaryServiceKey = mock<CompositeKey>()
    private val publicKeyNotaryVNode1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKeyNotaryVNode2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }

    private val signatureNotary1 = digitalSignatureAndMetadata(publicKeyNotaryVNode1, byteArrayOf(1, 2, 6))
    private val signatureNotary2 = digitalSignatureAndMetadata(publicKeyNotaryVNode2, byteArrayOf(1, 2, 7))

    private lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    private val argumentCaptor = argumentCaptor<Class<out AbstractUtxoLedgerExternalEventFactory<Any>>>()

    @BeforeEach
    fun setup() {
        utxoLedgerPersistenceService = UtxoLedgerPersistenceServiceImpl(
            currentSandboxGroupContext,
            externalEventExecutor,
            serializationService,
            utxoLedgerTransactionFactory,
            utxoSignedTransactionFactory,
            utxoFilteredTransactionFactory,
            notarySignatureVerificationService,
            stateAndRefCache
        )

        whenever(serializationService.serialize(any<Any>())).thenReturn(serializedBytes)
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(listOf(byteBuffer))

        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
        whenever(stateAndRefCache.putAll(any())).doAnswer {}

        // Composite key containing both of the notary VNode keys
        whenever(notaryServiceKey.leafKeys).thenReturn(setOf(publicKeyNotaryVNode1, publicKeyNotaryVNode2))
        whenever(notaryServiceKey.isFulfilledBy(publicKeyNotaryVNode1)).thenReturn(true)
        whenever(notaryServiceKey.isFulfilledBy(publicKeyNotaryVNode2)).thenReturn(true)
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
                VERIFIED
            )
        ).isEqualTo(listOf(expectedObj))

        verify(serializationService).serialize(any<Any>())
        verify(serializationService).deserialize<CordaPackageSummaryImpl>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `persistIfDoesNotExist returns a status of DOES_NOT_EXIST when null is returned from the external event factory`() {
        persistIfDoesNotExist(returnedStatus = null) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    VERIFIED
                )
            ).isEqualTo(TransactionExistenceStatus.DOES_NOT_EXIST to listOf(packageSummary))
        }
    }

    @Test
    fun `persistIfDoesNotExist returns a status of UNVERIFIED when UNVERIFIED is returned from the external event factory`() {
        persistIfDoesNotExist(UNVERIFIED.value) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    VERIFIED
                )
            ).isEqualTo(TransactionExistenceStatus.UNVERIFIED to listOf(packageSummary))
        }
    }

    @Test
    fun `persistIfDoesNotExist returns a status of VERIFIED when VERIFIED is returned from the external event factory`() {
        persistIfDoesNotExist(VERIFIED.value) { transaction, packageSummary ->
            assertThat(
                utxoLedgerPersistenceService.persistIfDoesNotExist(
                    transaction,
                    VERIFIED
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
                    VERIFIED
                )
            }.isExactlyInstanceOf(IllegalStateException::class.java)
        }
    }

    @Test
    fun `findSignedTransaction executes successfully`() {
        val metadata = mock<TransactionMetadata>()
        whenever(metadata.ledgerModel).thenReturn(UtxoLedgerTransactionImpl::class.java.name)
        whenever(metadata.transactionSubtype).thenReturn("GENERAL")
        val wireTransaction = mock<WireTransaction>()
        whenever(wireTransaction.componentGroupLists).thenReturn(List(UtxoComponentGroup.values().size) { listOf() })
        whenever(wireTransaction.metadata).thenReturn(metadata)

        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val expectedObj = UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            notarySignatureVerificationService,
            mock<UtxoLedgerTransactionFactory>(),
            wireTransaction,
            signatures
        )
        val testId = parseSecureHash("SHA256:1234567890123456")

        whenever(serializationService.deserialize<Pair<SignedTransactionContainer, String>>(any<ByteArray>(), any()))
            .thenReturn(SignedTransactionContainer(wireTransaction, signatures) to "V")

        whenever(utxoSignedTransactionFactory.create(any<WireTransaction>(), any())).thenReturn(expectedObj)

        assertThat(utxoLedgerPersistenceService.findSignedTransaction(testId)).isEqualTo(expectedObj)

        verify(serializationService).deserialize<UtxoSignedTransactionInternal>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(FindTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `findSignedLedgerTransaction executes successfully`() {
        val metadata = mock<TransactionMetadata>()
        val signedTransaction = mock<UtxoSignedTransactionInternal>()
        val ledgerTransaction = mock<UtxoLedgerTransactionInternal>()

        whenever(metadata.ledgerModel).thenReturn(UtxoLedgerTransactionImpl::class.java.name)
        whenever(metadata.transactionSubtype).thenReturn("GENERAL")
        val wireTransaction = mock<WireTransaction>()
        whenever(wireTransaction.componentGroupLists).thenReturn(List(UtxoComponentGroup.values().size) { listOf() })
        whenever(wireTransaction.metadata).thenReturn(metadata)

        val signatures = listOf(mock<DigitalSignatureAndMetadata>())
        val inputUtxoVisibleTransactionOutputDtos = listOf(UtxoVisibleTransactionOutputDto("tx1", 1, byteArrayOf(0), byteArrayOf(1)))
        val referenceUtxoVisibleTransactionOutputDtos = listOf(UtxoVisibleTransactionOutputDto("tx2", 1, byteArrayOf(0), byteArrayOf(1)))

        whenever(serializationService.deserialize<Pair<SignedLedgerTransactionContainer, String>>(any<ByteArray>(), any()))
            .thenReturn(
                SignedLedgerTransactionContainer(
                    wireTransaction,
                    inputUtxoVisibleTransactionOutputDtos,
                    referenceUtxoVisibleTransactionOutputDtos,
                    signatures
                ) to "V"
            )

        whenever(utxoSignedTransactionFactory.create(wireTransaction, signatures)).thenReturn(signedTransaction)

        whenever(
            utxoLedgerTransactionFactory.create(
                wireTransaction,
                inputUtxoVisibleTransactionOutputDtos,
                referenceUtxoVisibleTransactionOutputDtos
            )
        ).thenReturn(ledgerTransaction)

        assertThat(utxoLedgerPersistenceService.findSignedLedgerTransaction(parseSecureHash("SHA256:1234567890123456")))
            .isEqualTo(UtxoSignedLedgerTransactionImpl(ledgerTransaction, signedTransaction))

        assertThat(argumentCaptor.firstValue).isEqualTo(FindSignedLedgerTransactionExternalEventFactory::class.java)
    }

    @Test
    fun `findFilteredTransactionsAndSignatures executes successfully `() {
        val testId = parseSecureHash("SHA256:1234567890123456")

        val filteredTransaction = mock<FilteredTransaction>().also {
            whenever(it.getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)).thenReturn(
                listOf(Pair(1, byteArrayOf(1)))
            )
        }

        val filteredTransactionAndSignaturesMapA = mapOf(testId to Pair(filteredTransaction, listOf(signatureNotary1)))
        val filteredTransactionAndSignaturesMapB = mapOf(testId to Pair(filteredTransaction, listOf(signatureNotary2)))

        whenever(
            serializationService.deserialize(
                any<ByteArray>(),
                eq(Map::class.java)
            )
        ).thenReturn(
            filteredTransactionAndSignaturesMapA,
            filteredTransactionAndSignaturesMapB,
            filteredTransactionAndSignaturesMapA,
            filteredTransactionAndSignaturesMapB
        )

        val utxoFilteredTransaction = mock<UtxoFilteredTransaction>().also {
            whenever(it.notaryName).thenReturn(notaryX500Name)
        }

        whenever(utxoFilteredTransactionFactory.create(filteredTransaction)).thenReturn(utxoFilteredTransaction)
        whenever(notarySignatureVerificationService.verifyNotarySignatures(any(), any(), any(), any())).then {}
        val stateRef = mock<StateRef>().also {
            whenever(it.transactionId).thenReturn(testId)
        }

        val expectedResultA = mapOf(testId to UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransaction, listOf(signatureNotary1)))
        val expectedResultB = mapOf(testId to UtxoFilteredTransactionAndSignaturesImpl(utxoFilteredTransaction, listOf(signatureNotary2)))

        // assert with notary composite key
        assertThat(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                listOf(stateRef),
                notaryServiceKey,
                notaryX500Name
            )
        ).isEqualTo(expectedResultA)
        assertThat(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                listOf(stateRef),
                notaryServiceKey,
                notaryX500Name
            )
        ).isEqualTo(expectedResultB)

        // assert with notary public keys
        assertThat(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                listOf(stateRef),
                publicKeyNotaryVNode1,
                notaryX500Name
            )
        ).isEqualTo(expectedResultA)
        assertThat(
            utxoLedgerPersistenceService.findFilteredTransactionsAndSignatures(
                listOf(stateRef),
                publicKeyNotaryVNode2,
                notaryX500Name
            )
        ).isEqualTo(expectedResultB)

        assertThat(argumentCaptor.firstValue).isEqualTo(FindFilteredTransactionsAndSignaturesExternalEventFactory::class.java)
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
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

        verify(serializationService).serialize(any<Any>())
        verify(serializationService).deserialize<Pair<String?, List<CordaPackageSummary>>>(any<ByteArray>(), any())
        assertThat(argumentCaptor.firstValue).isEqualTo(PersistTransactionIfDoesNotExistExternalEventFactory::class.java)
    }
}
