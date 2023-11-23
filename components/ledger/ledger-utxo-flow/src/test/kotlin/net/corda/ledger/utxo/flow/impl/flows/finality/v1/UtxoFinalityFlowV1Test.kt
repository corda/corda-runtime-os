package net.corda.ledger.utxo.flow.impl.flows.finality.v1

import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.fullId
import net.corda.crypto.core.fullIdHash
import net.corda.flow.state.ContextPlatformProperties
import net.corda.flow.state.FlowContext
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.ledger.utxo.flow.impl.PluggableNotaryDetails
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.TransactionVerificationException
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.core.NotaryException
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class UtxoFinalityFlowV1Test {

    private companion object {
        val TX_ID = SecureHashImpl("algo", byteArrayOf(1, 2, 3))
        val ALICE = MemberX500Name("Alice", "London", "GB")
        val BOB = MemberX500Name("Bob", "London", "GB")
    }

    private val memberLookup = mock<MemberLookup>()
    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val transactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val platformProperties = mock<ContextPlatformProperties>().also { properties ->
        whenever(properties.set(any(), any())).thenAnswer {}
    }
    private val flowContextProperties = mock<FlowContext>().also {
        whenever(it.platformProperties).thenReturn(platformProperties)
    }
    private val flowEngine = mock<FlowEngine>().also {
        whenever(it.flowContextProperties).thenReturn(flowContextProperties)
    }
    private val flowMessaging = mock<FlowMessaging>()
    private val virtualNodeSelectorService = mock<NotaryVirtualNodeSelectorService>()
    private val visibilityChecker = mock<VisibilityChecker>()

    private val sessionAlice = mock<FlowSession>()
    private val sessionBob = mock<FlowSession>()
    private val sessions = setOf(sessionAlice, sessionBob)

    private val memberInfoAlice = mock<MemberInfo>()
    private val memberInfoBob = mock<MemberInfo>()

    private val publicKeyAlice1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x01)) }
    private val publicKeyAlice2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x02)) }
    private val publicKeyBob = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x03)) }

    private val publicKeyNotaryVNode1 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x04)) }
    private val publicKeyNotaryVNode2 = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x05)) }
    private val invalidNotaryVNodeKey = mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x06)) }

    private val notaryServiceKey = mock<CompositeKey>()

    private val signature0 = digitalSignatureAndMetadata(
        mock<PublicKey>().also { whenever(it.encoded).thenReturn(byteArrayOf(0x07)) },
        byteArrayOf(1, 2, 0)
    )
    private val signatureAlice1 = digitalSignatureAndMetadata(publicKeyAlice1, byteArrayOf(1, 2, 3))
    private val signatureAlice2 = digitalSignatureAndMetadata(publicKeyAlice2, byteArrayOf(1, 2, 4))
    private val signatureBob = digitalSignatureAndMetadata(publicKeyBob, byteArrayOf(1, 2, 5))

    private val signatureNotary = digitalSignatureAndMetadata(publicKeyNotaryVNode1, byteArrayOf(1, 2, 6))
    private val invalidNotarySignature = digitalSignatureAndMetadata(invalidNotaryVNodeKey, byteArrayOf(1, 2, 7))

    private val metadata = mock<TransactionMetadata>()

    private val initialTx = mock<UtxoSignedTransactionInternal>()
    private val updatedTxSomeSigs = mock<UtxoSignedTransactionInternal>()
    private val updatedTxAllSigs = mock<UtxoSignedTransactionInternal>()
    private val notarizedTx = mock<UtxoSignedTransactionInternal>()

    private val pluggableNotaryDetails = mock<PluggableNotaryDetails>()
    private val pluggableNotaryClientFlow = mock<PluggableNotaryClientFlow>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

    private val payloadCaptor = argumentCaptor<Payload<*>>()
    private val transactionState = mock<TransactionState<TestState>>()
    private val stateAndRef = mock<StateAndRef<TestState>>()
    private val testState = TestState(listOf(publicKeyAlice1))

    @BeforeEach
    fun beforeEach() {
        whenever(sessionAlice.counterparty).thenReturn(ALICE)
        whenever(sessionAlice.receive(Unit::class.java)).thenReturn(Unit)
        whenever(sessionBob.counterparty).thenReturn(BOB)
        whenever(sessionBob.receive(Unit::class.java)).thenReturn(Unit)

        whenever(memberLookup.myInfo()).thenReturn(memberInfoAlice)
        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))
        whenever(memberInfoAlice.toString()).thenReturn(ALICE.toString())
        whenever(memberInfoBob.ledgerKeys).thenReturn(listOf(publicKeyBob))
        whenever(memberInfoBob.toString()).thenReturn(BOB.toString())

        whenever(flowEngine.subFlow(any<TransactionBackchainSenderFlow>())).thenReturn(Unit)

        whenever(initialTx.id).thenReturn(TX_ID)
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )
        whenever(initialTx.toLedgerTransaction()).thenReturn(ledgerTransaction)
        whenever(initialTx.metadata).thenReturn(metadata)
        whenever(initialTx.notaryName).thenReturn(notaryX500Name)
        whenever(initialTx.notaryKey).thenReturn(publicKeyExample)
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(updatedTxSomeSigs)
        whenever(initialTx.signatures).thenReturn(listOf(signature0))

        whenever(updatedTxSomeSigs.id).thenReturn(TX_ID)
        whenever(updatedTxSomeSigs.addSignature(signatureAlice2)).thenReturn(
            updatedTxSomeSigs
        )
        whenever(updatedTxSomeSigs.addSignature(signatureBob)).thenReturn(
            updatedTxAllSigs
        )
        whenever(updatedTxAllSigs.id).thenReturn(TX_ID)
        whenever(updatedTxAllSigs.notaryName).thenReturn(notaryX500Name)
        whenever(updatedTxAllSigs.notaryKey).thenReturn(notaryServiceKey)
        whenever(updatedTxAllSigs.addSignature(signatureNotary)).thenReturn(
            notarizedTx
        )

        whenever(ledgerTransaction.id).thenReturn(SecureHashImpl("algo", byteArrayOf(1, 2, 11)))
        whenever(ledgerTransaction.outputContractStates).thenReturn(listOf(getUtxoStateExample()))
        whenever(ledgerTransaction.signatories).thenReturn(listOf(publicKeyExample))
        whenever(ledgerTransaction.commands).thenReturn(listOf(UtxoCommandExample()))
        whenever(ledgerTransaction.timeWindow).thenReturn(utxoTimeWindowExample)
        whenever(ledgerTransaction.metadata).thenReturn(metadata)

        // Composite key containing both of the notary VNode keys
        whenever(notaryServiceKey.leafKeys).thenReturn(setOf(publicKeyNotaryVNode1, publicKeyNotaryVNode2))

        // Single output State
        whenever(stateAndRef.state).thenReturn(transactionState)
        whenever(transactionState.contractType).thenReturn(TestContact::class.java)
        whenever(transactionState.contractState).thenReturn(testState)

        whenever(pluggableNotaryDetails.flowClass).thenReturn(pluggableNotaryClientFlow.javaClass)
    }

    @Test
    fun `called with a transaction initially without signatures throws and persists as invalid`() {
        whenever(initialTx.signatures).thenReturn(listOf())
        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Received initial transaction without signatures.")

        verify(initialTx, never()).addMissingSignatures()
        verify(persistenceService).persist(initialTx, TransactionStatus.INVALID)
    }

    @Test
    fun `called with a transaction initially with invalid signature throws and persists as invalid`() {
        whenever(initialTx.verifySignatorySignature(any())).thenThrow(
            CryptoSignatureException("Verifying signature failed!!")
        )
        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)
            .hasMessageContaining("Verifying signature failed!!")

        verify(initialTx, never()).addMissingSignatures()
        verify(persistenceService).persist(initialTx, TransactionStatus.INVALID)
    }

    @Test
    fun `called with an invalid transaction initially throws and persists as invalid`() {
        whenever(transactionVerificationService.verify(any())).thenThrow(
            TransactionVerificationException(
                TX_ID, TransactionVerificationStatus.INVALID, null, "Verification error"
            )
        )
        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("Verification error")

        verify(initialTx, never()).addMissingSignatures()
        verify(persistenceService).persist(initialTx, TransactionStatus.INVALID)
    }

    @Test
    fun `receiving valid signatures over a transaction with successful notarization (from one of the notary VNodes) leads to it being recorded and distributed for recording to passed in sessions`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)
        whenever(txAfterBobSignature.addSignature(signatureNotary)).thenReturn(notarizedTx)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))
        whenever(notarizedTx.outputStateAndRefs).thenReturn(listOf(stateAndRef))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyAlice1))).thenReturn(true)
        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyBob))).thenReturn(true)

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))
        verify(txAfterBobSignature).verifyNotarySignature(eq(signatureNotary))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED, emptyList())
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED, emptyList())
        verify(persistenceService).persist(notarizedTx, TransactionStatus.VERIFIED, listOf(0))

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging).sendAll(Payload.Success(listOf(signatureNotary)), sessions)
    }

    @Test
    fun `receiving valid signatures over a transaction then permanent failing notarization throws and invalidates tx`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        @CordaSerializable
        class TestNotaryExceptionFatal(
            errorText: String,
            txId: SecureHash? = null
        ) : NotaryExceptionFatal(errorText, txId)

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenThrow(
            TestNotaryExceptionFatal("notarization error", null)
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(NotaryException::class.java)
            .hasMessageContaining("notarization error")

        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.INVALID)

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
        verify(flowMessaging).sendAll(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Notarization failed permanently with Unable to notarize transaction <Unknown>:" +
                        " notarization error.",
                FinalityNotarizationFailureType.FATAL.value
            ), sessions
        )
    }

    @Test
    fun `receiving valid signatures over a transaction then non-permanent failing notarization throws, but does not invalidate tx`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        @CordaSerializable
        class TestNotaryExceptionNonFatal(
            errorText: String,
            txId: SecureHash? = null
        ) : NotaryExceptionUnknown(errorText, txId)

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow))
            .thenThrow(TestNotaryExceptionNonFatal("notarization error"))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Unable to notarize transaction <Unknown>: notarization error")

        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.INVALID), any())

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
        verify(flowMessaging).sendAll(
            Payload.Failure<List<DigitalSignatureAndMetadata>>(
                "Notarization failed with Unable to notarize transaction <Unknown>: notarization error.",
                FinalityNotarizationFailureType.UNKNOWN.value
            ), sessions
        )
    }

    @Test
    fun `receiving a signature from a notary that is not part of the notary service composite key throws an error`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)
        whenever(txAfterBobSignature.addSignature(invalidNotarySignature)).thenReturn(notarizedTx)

        whenever(txAfterBobSignature.verifyNotarySignature(invalidNotarySignature)).thenThrow(
            TransactionSignatureException(TX_ID, "Notary's signature has not been created by the transaction's notary", null)
        )
        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(invalidNotarySignature))
        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionSignatureException::class.java)
            .hasMessageContaining("Notary's signature has not been created by the transaction's notary")

        verify(initialTx).verifySignatorySignature(eq(signature0))
        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))
        verify(txAfterBobSignature).verifyNotarySignature(eq(invalidNotarySignature))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature, never()).addSignature(invalidNotarySignature)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging).sendAll(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>(), eq(sessions))
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(invalidNotarySignature))), any())
    }

    @Test
    fun `receiving valid signatures over a transaction then receiving no signatures from notary throws`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf())

        doNothing().whenever(flowMessaging).sendAll(payloadCaptor.capture(), eq(sessions))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Notary")
            .hasMessageContaining("did not return any signatures after requesting notarization of transaction")

        verify(initialTx).verifySignatorySignature(eq(signature0))
        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))
        verify(txAfterBobSignature, never()).verifyNotarySignature(any())

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
        verify(flowMessaging).sendAll(any<Payload.Failure<*>>(), eq(sessions))
        assertThat((payloadCaptor.firstValue as Payload.Failure<*>).message)
            .contains("Notary")
            .contains("did not return any signatures after requesting notarization of transaction")
    }

    @Test
    fun `receiving valid signatures over a transaction then receiving invalid notary signatures throws`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))
        whenever(txAfterBobSignature.verifyNotarySignature(eq(signatureNotary))).thenThrow(
            IllegalArgumentException("Notary signature verification failed.")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Notary signature verification failed.")

        verify(initialTx).verifySignatorySignature(eq(signature0))
        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature).verifySignatorySignature(eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature).addSignature(signatureBob)
        verify(txAfterBobSignature, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging).sendAll(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>(), eq(sessions))
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
    }

    @Test
    fun `ledger keys from the passed in sessions can contain more than the missing signatories`() {
        whenever(memberInfoAlice.ledgerKeys).thenReturn(listOf(publicKeyAlice1, publicKeyAlice2))

        whenever(initialTx.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyBob))

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))
        whenever(txAfterAlice1Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)
        whenever(txAfterBobSignature.addSignature(signatureNotary)).thenReturn(notarizedTx)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature, never()).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureBob))
        verify(txAfterBobSignature).verifyNotarySignature(eq(signatureNotary))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature, never()).addSignature(signatureAlice2)
        verify(txAfterAlice1Signature).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterBobSignature, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarizedTx, TransactionStatus.VERIFIED)

        verify(flowMessaging).receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1)
            )
        )
        verify(flowMessaging).sendAll(Payload.Success(listOf(signatureNotary)), sessions)
    }

    @Test
    fun `receiving a session error instead of signatures rethrows the error`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService, never()).verifySignature(eq(updatedTxSomeSigs), any(), any())

        verify(initialTx, never()).addSignature(any())
        verify(updatedTxSomeSigs, never()).addSignature(any())
        verify(updatedTxSomeSigs, never()).addSignature(any())

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(initialTx, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a failure payload throws an exception`() {
        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Failure<DigitalSignatureAndMetadata>(
                    "message!",
                    "reason"
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Failed to receive signatures from $BOB for transaction ${initialTx.id} with message: message!")

        verify(initialTx).verifySignatorySignature(eq(signature0))
        verify(initialTx).verifySignatorySignature(eq(signatureAlice1))
        verify(txAfterAlice1Signature).verifySignatorySignature(eq(signatureAlice2))
        verify(txAfterAlice2Signature, never()).verifySignatorySignature(eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(initialTx, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `failing to verify a received signature throws an exception`() {
        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )


        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)

        whenever(txAfterAlice2Signature.verifySignatorySignature(eq(signatureBob))).thenThrow(
            CryptoSignatureException("")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)

        verify(initialTx).addSignature(signatureAlice1)
        verify(txAfterAlice1Signature).addSignature(signatureAlice2)
        verify(txAfterAlice2Signature, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(txAfterAlice2Signature, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `missing signatures when verifying all signatures rethrows exception with useful message`() {
        val aliceSignatures = listOf(signatureAlice1, signatureAlice2)

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(aliceSignatures),
                sessionBob to Payload.Success(
                    emptyList<Payload<DigitalSignatureAndMetadata>>()
                )
            )
        )

        whenever(updatedTxSomeSigs.verifySignatorySignatures()).thenThrow(
            TransactionMissingSignaturesException(TX_ID, setOf(publicKeyBob), "missing")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionMissingSignaturesException::class.java)
            .hasMessageContainingAll(
                "Transaction $TX_ID is missing signatures for signatories (key ids) ${setOf(publicKeyBob).map { it.fullId() }}. ",
                "The following counterparties provided signatures while finalizing the transaction:",
                "$ALICE provided 2 signature(s) to satisfy the signatories (key ids) ${aliceSignatures.map { it.by }}",
                "$BOB provided 0 signature(s) to satisfy the signatories (key ids) []"
            )

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxSomeSigs, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `failing to verify all signatures throws exception`() {
        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(listOf(signatureBob))
            )
        )

        whenever(updatedTxAllSigs.verifySignatorySignatures()).thenThrow(
            TransactionMissingSignaturesException(
                TX_ID,
                setOf(),
                "failed"
            )
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionMissingSignaturesException::class.java)
            .hasMessageContaining("is missing signatures for signatories")

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.INVALID)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `each passed in session is sent the transaction backchain`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))
        whenever(initialTx.inputStateRefs).thenReturn(listOf(mock()))
        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(listOf(signatureBob))
            )
        )

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionBob))
    }

    @Test
    fun `called with a transaction that has no dependencies should not invoke backchain resolution`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))
        whenever(initialTx.inputStateRefs).thenReturn(emptyList())
        whenever(initialTx.referenceStateRefs).thenReturn(emptyList())

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(listOf(signatureBob))
            )
        )

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(flowEngine, never()).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionAlice))
        verify(flowEngine, never()).subFlow(TransactionBackchainSenderFlow(TX_ID, sessionBob))
    }

    @Test
    fun `do not send unseen signatures to counterparties when there are only two parties`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(publicKeyBob)
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(initialTx.addSignature(signatureBob)).thenReturn(txAfterBobSignature)
        whenever(txAfterBobSignature.addSignature(signatureNotary)).thenReturn(notarizedTx)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureBob))
        whenever(notarizedTx.outputStateAndRefs).thenReturn(listOf(stateAndRef))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyBob))).thenReturn(true)

        callFinalityFlow(initialTx, listOf(sessionBob))

        verify(flowMessaging, never()).sendAllMap(mapOf())
    }

    @Test
    fun `sending unseen signatures to counterparties when there more than two parties`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(flowMessaging.receiveAllMap(mapOf(
            sessionAlice to Payload::class.java,
            sessionBob to Payload::class.java
        ))).thenReturn(
            mapOf(
                sessionAlice to Payload.Success(
                    listOf(
                        signatureAlice1,
                        signatureAlice2
                    )
                ),
                sessionBob to Payload.Success(
                    listOf(
                        signatureBob
                    )
                )
            )
        )

        val txAfterAlice1Signature = mock<UtxoSignedTransactionInternal>()
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(txAfterAlice1Signature)
        val txAfterAlice2Signature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterAlice1Signature.addSignature(signatureAlice2)).thenReturn(txAfterAlice2Signature)
        val txAfterBobSignature = mock<UtxoSignedTransactionInternal>()
        whenever(txAfterBobSignature.notaryName).thenReturn(notaryX500Name)
        whenever(txAfterAlice2Signature.addSignature(signatureBob)).thenReturn(txAfterBobSignature)
        whenever(txAfterBobSignature.addSignature(signatureNotary)).thenReturn(notarizedTx)

        whenever(txAfterBobSignature.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))
        whenever(notarizedTx.outputStateAndRefs).thenReturn(listOf(stateAndRef))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyAlice1))).thenReturn(true)
        whenever(visibilityChecker.containsMySigningKeys(listOf(publicKeyBob))).thenReturn(true)

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
    }


    private fun callFinalityFlow(signedTransaction: UtxoSignedTransactionInternal, sessions: List<FlowSession>) {
        val flow = spy(UtxoFinalityFlowV1(
            signedTransaction,
            sessions,
            pluggableNotaryDetails
        ))
//        doReturn(pluggableNotaryDetails).whenever(pluggableNotaryClientFlow.javaClass)
        doReturn(pluggableNotaryClientFlow).whenever(flow).newPluggableNotaryClientFlowInstance(any())

        flow.memberLookup = memberLookup
        flow.flowEngine = flowEngine
        flow.flowMessaging = flowMessaging
        flow.persistenceService = persistenceService
        flow.transactionVerificationService = transactionVerificationService
        flow.virtualNodeSelectorService = virtualNodeSelectorService
        flow.visibilityChecker = visibilityChecker
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignatureWithKeyId(publicKey.fullIdHash(), byteArray),
            DigitalSignatureMetadata(Instant.now(), SignatureSpecImpl("dummySignatureName"), emptyMap())
        )
    }

    class TestContact : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) {
        }
    }

    class TestState(private val participants: List<PublicKey>) : ContractState {

        override fun getParticipants(): List<PublicKey> {
            return participants
        }
    }
}
