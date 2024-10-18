package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.crypto.core.parseSecureHash
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.internal.serialization.SerializedBytesImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.TransactionVerificationResult
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.external.events.ALICE_X500_HOLDING_IDENTITY
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class UtxoLedgerTransactionVerificationServiceImplTest {

    private companion object {
        private val byteBuffer = ByteBuffer.wrap("bytes".toByteArray())
        private val serializedBytes = SerializedBytesImpl<Any>(byteBuffer.array())
    }

    private val externalEventExecutor = mock<ExternalEventExecutor>()
    private val serializationService = mock<SerializationService>()
    private lateinit var verificationService: UtxoLedgerTransactionVerificationServiceImpl
    private val argumentCaptor = argumentCaptor<Class<TransactionVerificationExternalEventFactory>>()
    private val mockUtxoLedgerGroupParametersPersistenceService = mock<UtxoLedgerGroupParametersPersistenceService>()
    private val mockSignedGroupParameters = mock<SignedGroupParameters>()
    private val notaryInfo = mock<NotaryInfo>()
    private val sandbox = mock<SandboxGroupContext>()
    private val virtualNodeContext = mock<VirtualNodeContext>()
    private val currentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    private val groupParametersHash = parseSecureHash("algo:1234")

    @BeforeEach
    fun setup() {
        verificationService = UtxoLedgerTransactionVerificationServiceImpl(
            externalEventExecutor,
            serializationService,
            currentSandboxGroupContext,
            mock()
        )

        whenever(serializationService.serialize(any<Any>())).thenReturn(serializedBytes)
        whenever(mockUtxoLedgerGroupParametersPersistenceService.find(any())).thenReturn(mockSignedGroupParameters)
        whenever(notaryInfo.name).thenReturn(notaryX500Name)
        whenever(notaryInfo.publicKey).thenReturn(publicKeyExample)
        whenever(mockSignedGroupParameters.notaries).thenReturn(listOf(notaryInfo))
        whenever(mockSignedGroupParameters.hash).thenReturn(groupParametersHash)
        whenever(sandbox.virtualNodeContext).thenReturn(virtualNodeContext)
        whenever(virtualNodeContext.holdingIdentity).thenReturn(ALICE_X500_HOLDING_IDENTITY.toCorda())
        whenever(currentSandboxGroupContext.get()).thenReturn(sandbox)
    }

    @Test
    fun `verification of valid transaction`() {
        val expectedObj = TransactionVerificationResult(
            TransactionVerificationStatus.VERIFIED,
            errorType = null,
            errorMessage = null
        )
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(expectedObj)

        val transaction = mock<UtxoLedgerTransactionInternal>()
        val wireTransaction = mock<WireTransaction>()
        val transactionMetadata = mock<TransactionMetadataInternal>()
        whenever(transaction.wireTransaction).thenReturn(wireTransaction)
        whenever(transaction.metadata).thenReturn(transactionMetadata)
        whenever(transaction.notaryKey).thenReturn(publicKeyExample)
        whenever(transaction.notaryName).thenReturn(notaryX500Name)
        whenever(transaction.groupParameters).thenReturn(mockSignedGroupParameters)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(transactionMetadata.getCpkMetadata()).thenReturn(listOf(mock()))
        whenever(transactionMetadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash.toString())

        assertDoesNotThrow {
            verificationService.verify(transaction)
        }

        verify(serializationService).serialize(any<Any>())
        assertThat(argumentCaptor.firstValue).isEqualTo(TransactionVerificationExternalEventFactory::class.java)
    }

    @Test
    fun `verification of invalid transaction results with exception`() {
        val expectedObj = TransactionVerificationResult(
            TransactionVerificationStatus.INVALID,
            errorType = "net.corda.v5.ledger.utxo.ContractVerificationException",
            errorMessage = "Contract verification failed"
        )
        whenever(
            externalEventExecutor.execute(
                argumentCaptor.capture(),
                any()
            )
        ).thenReturn(expectedObj)

        val transactionId = mock<SecureHash>()
        val transaction = mock<UtxoLedgerTransactionInternal>()
        val wireTransaction = mock<WireTransaction>()
        val transactionMetadata = mock<TransactionMetadataInternal>()
        whenever(transaction.id).thenReturn(transactionId)
        whenever(transaction.wireTransaction).thenReturn(wireTransaction)
        whenever(transaction.metadata).thenReturn(transactionMetadata)
        whenever(transaction.notaryKey).thenReturn(publicKeyExample)
        whenever(transaction.notaryName).thenReturn(notaryX500Name)
        whenever(transaction.groupParameters).thenReturn(mockSignedGroupParameters)
        whenever(wireTransaction.metadata).thenReturn(transactionMetadata)
        whenever(transactionMetadata.getCpkMetadata()).thenReturn(listOf(mock()))
        whenever(transactionMetadata.getMembershipGroupParametersHash()).thenReturn(groupParametersHash.toString())

        val exception = assertThrows<TransactionVerificationException> {
            verificationService.verify(transaction)
        }

        assertThat(exception.transactionId).isEqualTo(transactionId)
        assertThat(exception.status).isEqualTo(expectedObj.status)
        assertThat(exception.originalExceptionClassName).isEqualTo(expectedObj.errorType)
        assertThat(exception.status).isEqualTo(expectedObj.status)

        verify(serializationService).serialize(any<Any>())
        assertThat(argumentCaptor.firstValue).isEqualTo(TransactionVerificationExternalEventFactory::class.java)
    }
}
