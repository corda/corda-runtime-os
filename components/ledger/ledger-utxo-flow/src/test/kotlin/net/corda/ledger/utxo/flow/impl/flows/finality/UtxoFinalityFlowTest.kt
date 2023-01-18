package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

@Suppress("MaxLineLength")
class UtxoFinalityFlowTest {

    private companion object {
        val TX_ID = SecureHash("algo", byteArrayOf(1, 2, 3))
        val ALICE = MemberX500Name("Alice", "London", "GB")
        val BOB = MemberX500Name("Bob", "London", "GB")
    }

    private val memberLookup = mock<MemberLookup>()
    private val transactionSignatureService = mock<TransactionSignatureService>()
    private val persistenceService = mock<UtxoLedgerPersistenceService>()
    private val flowEngine = mock<FlowEngine>()
    private val flowMessaging = mock<FlowMessaging>()
    private val pluggableNotaryClientFlowFactory = mock<PluggableNotaryClientFlowFactory>()

    private val sessionAlice = mock<FlowSession>()
    private val sessionBob = mock<FlowSession>()
    private val sessions = setOf(sessionAlice, sessionBob)

    private val memberInfoAlice = mock<MemberInfo>()
    private val memberInfoBob = mock<MemberInfo>()

    private val publicKeyAlice1 = mock<PublicKey>()
    private val publicKeyAlice2 = mock<PublicKey>()
    private val publicKeyBob = mock<PublicKey>()

    private val publicKeyNotaryVNode1 = mock<PublicKey>()
    private val publicKeyNotaryVNode2 = mock<PublicKey>()
    private val invalidNotaryVNodeKey = mock<PublicKey>()

    private val notaryService = mock<Party>()
    private val notaryServiceKey = mock<CompositeKey>()

    private val signatureAlice1 = digitalSignatureAndMetadata(publicKeyAlice1, byteArrayOf(1, 2, 3))
    private val signatureAlice2 = digitalSignatureAndMetadata(publicKeyAlice2, byteArrayOf(1, 2, 4))
    private val signatureBob = digitalSignatureAndMetadata(publicKeyBob, byteArrayOf(1, 2, 5))

    private val signatureNotary = digitalSignatureAndMetadata(publicKeyNotaryVNode1, byteArrayOf(1, 2, 6))
    private val invalidNotarySignature = digitalSignatureAndMetadata(invalidNotaryVNodeKey, byteArrayOf(1, 2, 7))

    private val metadata = mock<TransactionMetadata>()

    private val initialTx = mock<UtxoSignedTransactionInternal>()
    private val updatedTxSomeSigs = mock<UtxoSignedTransactionInternal>()
    private val updatedTxAllSigs = mock<UtxoSignedTransactionInternal>()
    private val notarisedTx = mock<UtxoSignedTransactionInternal>()

    private val pluggableNotaryClientFlow = mock<PluggableNotaryClientFlow>()
    private val ledgerTransaction = mock<UtxoLedgerTransaction>()

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
        whenever(initialTx.notary).thenReturn(utxoNotaryExample)
        whenever(initialTx.addSignature(signatureAlice1)).thenReturn(updatedTxSomeSigs)

        whenever(updatedTxSomeSigs.id).thenReturn(TX_ID)
        whenever(updatedTxSomeSigs.addSignature(signatureAlice2)).thenReturn(
            updatedTxSomeSigs
        )
        whenever(updatedTxSomeSigs.addSignature(signatureBob)).thenReturn(
            updatedTxAllSigs
        )
        whenever(updatedTxAllSigs.id).thenReturn(TX_ID)
        whenever(updatedTxAllSigs.notary).thenReturn(notaryService)
        whenever(updatedTxAllSigs.addSignature(signatureNotary)).thenReturn(
            notarisedTx
        )

        whenever(ledgerTransaction.id).thenReturn(SecureHash("algo", byteArrayOf(1, 2, 11)))
        whenever(ledgerTransaction.outputContractStates).thenReturn(listOf(utxoStateExample))
        whenever(ledgerTransaction.signatories).thenReturn(listOf(publicKeyExample))
        whenever(ledgerTransaction.commands).thenReturn(listOf(UtxoCommandExample()))
        whenever(ledgerTransaction.timeWindow).thenReturn(utxoTimeWindowExample)

        whenever(pluggableNotaryClientFlowFactory.create(eq(notaryService), any<UtxoSignedTransaction>())).thenReturn(
            pluggableNotaryClientFlow
        )

        // Composite key containing both of the notary VNode keys
        whenever(notaryServiceKey.leafKeys).thenReturn(setOf(publicKeyNotaryVNode1, publicKeyNotaryVNode2))
        whenever(notaryService.owningKey).thenReturn(notaryServiceKey)
    }

    @Test
    fun `receiving valid signatures over a transaction with successful notarisation (from one of the notary VNodes) leads to it being recorded and distributed for recording to passed in sessions`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))
        verify(transactionSignatureService).verifyNotarySignature(any(), eq(signatureNotary))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarisedTx, TransactionStatus.VERIFIED)

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging).sendAll(Payload.Success(listOf(signatureNotary)), sessions)
    }

    @Test
    fun `receiving valid signatures over a transaction then failing notarisation throws`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenThrow(CordaRuntimeException("notarisation error"))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("notarisation error")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
        verify(flowMessaging).sendAll(any<Payload.Failure<List<DigitalSignatureAndMetadata>>>(), eq(sessions))
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

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(invalidNotarySignature))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Notary's signature has not been created by the transaction's notary")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))
        verify(transactionSignatureService, never()).verifyNotarySignature(any(), eq(invalidNotarySignature))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs, never()).addSignature(invalidNotarySignature)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
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

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf())

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Notary has not returned any signatures.")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
        verify(flowMessaging).sendAllMap(
            mapOf(
                sessionAlice to listOf(signatureBob),
                sessionBob to listOf(signatureAlice1, signatureAlice2)
            )
        )
        verify(flowMessaging, never()).sendAll(eq(Payload.Success(listOf(signatureNotary))), any())
        verify(flowMessaging).sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>("Notary has not returned any signatures."), sessions)
    }

    @Test
    fun `receiving valid signatures over a transaction then receiving invalid signatures from notary throws`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))
        whenever(transactionSignatureService.verifyNotarySignature(any(), eq(signatureNotary))).thenThrow(
            IllegalArgumentException("Notary signature verification failed.")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Notary signature verification failed.")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
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
    fun `receiving valid signatures over a transaction then receiving signatures from notary with unexpected signer throws`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(
            setOf(
                publicKeyAlice1,
                publicKeyAlice2,
                publicKeyBob
            )
        )

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureAlice2, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))
        whenever(notaryService.owningKey).thenReturn(publicKeyAlice1)

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Notary's signature has not been created by the transaction's notary.")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)
        verify(updatedTxAllSigs, never()).addSignature(signatureNotary)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
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

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )
        whenever(updatedTxAllSigs.signatures).thenReturn(listOf(signatureAlice1, signatureBob))

        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureBob))
        verify(transactionSignatureService).verifyNotarySignature(any(), eq(signatureNotary))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs, never()).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(updatedTxAllSigs, TransactionStatus.UNVERIFIED)
        verify(persistenceService).persist(notarisedTx, TransactionStatus.VERIFIED)

        verify(sessionAlice).receive(Payload::class.java)
        verify(sessionBob).receive(Payload::class.java)
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
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java,)).thenThrow(CordaRuntimeException("session error"))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("session error")

        verify(transactionSignatureService, never()).verifySignature(any(), any())

        verify(initialTx, never()).addSignature(any())
        verify(updatedTxSomeSigs, never()).addSignature(any())
        verify(updatedTxSomeSigs, never()).addSignature(any())

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `receiving a failure payload throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Failure<DigitalSignatureAndMetadata>("message!", "reason"))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessage("Failed to receive signatures from $BOB for transaction ${initialTx.id} with message: message!")

        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice1))
        verify(transactionSignatureService).verifySignature(any(), eq(signatureAlice2))
        verify(transactionSignatureService, never()).verifySignature(any(), eq(signatureBob))

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `failing to verify a received signature throws an exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureAlice1,
                    signatureAlice2
                )
            )
        )
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                listOf(
                    signatureBob
                )
            )
        )

        whenever(transactionSignatureService.verifySignature(any(), eq(signatureBob))).thenThrow(
            CryptoSignatureException("")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(CryptoSignatureException::class.java)

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `missing signatures when verifying all signatures rethrows exception with useful message`() {
        val aliceSignatures = listOf(signatureAlice1, signatureAlice2)

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(aliceSignatures))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(
            Payload.Success(
                emptyList<Payload<DigitalSignatureAndMetadata>>()
            )
        )

        whenever(updatedTxSomeSigs.verifySignatures()).thenThrow(
            TransactionMissingSignaturesException(TX_ID, setOf(publicKeyBob), "missing")
        )

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionMissingSignaturesException::class.java)
            .hasMessageContainingAll(
                "Transaction $TX_ID is missing signatures for signatories (encoded) ${setOf(publicKeyBob).map { it.encoded }}",
                "The following counterparties provided signatures while finalizing the transaction:",
                "$ALICE provided 2 signature(s) to satisfy the signatories (encoded) ${aliceSignatures.map { it.by.encoded }}",
                "$BOB provided 0 signature(s) to satisfy the signatories (encoded) []"
            )

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs, never()).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `failing to verify all signatures throws exception`() {
        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        whenever(updatedTxAllSigs.verifySignatures()).thenThrow(TransactionVerificationException(TX_ID, "failed", null))

        assertThatThrownBy { callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob)) }
            .isInstanceOf(TransactionVerificationException::class.java)
            .hasMessageContaining("failed")

        verify(initialTx).addSignature(signatureAlice1)
        verify(updatedTxSomeSigs).addSignature(signatureAlice2)
        verify(updatedTxSomeSigs).addSignature(signatureBob)

        verify(persistenceService).persist(initialTx, TransactionStatus.UNVERIFIED)
        verify(persistenceService, never()).persist(any(), eq(TransactionStatus.VERIFIED), any())
    }

    @Test
    fun `each passed in session is sent the transaction backchain`() {
        whenever(initialTx.getMissingSignatories()).thenReturn(setOf(publicKeyAlice1, publicKeyAlice2, publicKeyBob))
        whenever(flowEngine.subFlow(pluggableNotaryClientFlow)).thenReturn(listOf(signatureNotary))

        whenever(sessionAlice.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureAlice1, signatureAlice2)))
        whenever(sessionBob.receive(Payload::class.java)).thenReturn(Payload.Success(listOf(signatureBob)))

        callFinalityFlow(initialTx, listOf(sessionAlice, sessionBob))

        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(sessionAlice))
        verify(flowEngine).subFlow(TransactionBackchainSenderFlow(sessionBob))
    }

    private fun callFinalityFlow(signedTransaction: UtxoSignedTransactionInternal, sessions: List<FlowSession>) {
        val flow = UtxoFinalityFlow(signedTransaction, sessions)
        flow.memberLookup = memberLookup
        flow.transactionSignatureService = transactionSignatureService
        flow.flowEngine = flowEngine
        flow.flowMessaging = flowMessaging
        flow.persistenceService = persistenceService
        flow.pluggableNotaryClientFlowFactory = pluggableNotaryClientFlowFactory
        flow.flowEngine = flowEngine
        flow.call()
    }

    private fun digitalSignatureAndMetadata(publicKey: PublicKey, byteArray: ByteArray): DigitalSignatureAndMetadata {
        return DigitalSignatureAndMetadata(
            DigitalSignature.WithKey(publicKey, byteArray, emptyMap()),
            DigitalSignatureMetadata(Instant.now(), emptyMap())
        )
    }
}
