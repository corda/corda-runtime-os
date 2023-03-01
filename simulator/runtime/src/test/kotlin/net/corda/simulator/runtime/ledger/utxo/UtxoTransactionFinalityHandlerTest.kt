package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.BaseMemberInfo
import net.corda.simulator.runtime.notary.SimTimeWindow
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import java.time.Instant

class UtxoTransactionFinalityHandlerTest {
    private val alice = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val notaryX500 = MemberX500Name.parse("CN=SimulatorNotaryService, OU=Simulator, O=R3, L=London, C=GB")
    private val config = SimulatorConfigurationBuilder.create().build()
    private val publicKeys = generateKeys(3)
    private val notary = Party(notaryX500, generateKey())

    @Test
    fun `should be able to collect signature and notarize a transaction`() {
        //Given a signed utxo transaction
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        val transaction = UtxoSignedTransactionBase(
            listOf(toSignatureWithMetadata(publicKeys[0])),
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(
                    ContractStateAndEncumbranceTag(TestUtxoState("StateData", publicKeys), ""),
                ),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        val sessions = publicKeys.minus(publicKeys[0]).map {
            val signature = DigitalSignatureAndMetadata(
                toSignatureWithMetadata(it).signature,
                DigitalSignatureMetadata(Instant.now(), SignatureSpec("dummySignatureName"), mapOf())
            )
            val flowSession = mock<FlowSession>()
            whenever(flowSession.receive<Any>(any())).thenReturn(listOf(signature))
            flowSession
        }

        val memberLookup = mock<MemberLookup>()
        whenever(memberLookup.lookup(eq(notaryX500))).thenReturn(BaseMemberInfo(notaryX500, listOf(notary.owningKey)))
        whenever(signingService.sign(any(), eq(notary.owningKey), any()))
            .thenReturn(toSignatureWithMetadata(notary.owningKey).signature)
        whenever(memberLookup.myInfo()).thenReturn(BaseMemberInfo(alice, listOf( publicKeys[0])))

        //When we call finality on the transaction
        val finalizer = UtxoTransactionFinalityHandler(
            memberLookup, signingService, signingService, persistenceService, mock())
        val finalTx = finalizer.finalizeTransaction(transaction, sessions)

        // Then the transaction should get signed by the counterparties and notary
        assertThat(finalTx.id, `is`(transaction.id))
        assertThat(finalTx.signatures.size, `is`(4))
        assertThat(finalTx.signatures.map { it.by }.toSet(), `is`(publicKeys.plus(notary.owningKey).toSet()))

        // And it should have been persisted
        verify(persistenceService, times(1)).persist(
            (finalTx as UtxoSignedTransactionBase).toEntity()
        )
        verify(persistenceService, times(1)).persist(
            finalTx.toOutputsEntity(setOf( publicKeys[0]))
        )
    }

    @Test
    fun `should sign transaction when receive finality is called then receive fully-signed transaction`(){
        // Given a signed transaction is generated with all keys except for publicKey[1]
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        publicKeys.forEach {
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(toSignatureWithMetadata(it).signature)
        }

        val signedTransaction = UtxoSignedTransactionBase(
            listOf(toSignatureWithMetadata(publicKeys[0])),
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(
                    ContractStateAndEncumbranceTag(TestUtxoState("StateData", publicKeys), ""),
                ),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )

        val twiceSignedTransaction = signedTransaction
            .addSignatures(listOf( publicKeys[1]))
        val thriceSignedTransaction = twiceSignedTransaction
            .addSignatures(listOf( publicKeys[2]))

        // And a flow session is created that will send the first transaction to be signed,
        // followed by the fully-signed transaction for counterparty records
        val flowSession = mock<FlowSession>()
        whenever(flowSession.receive<Any>(any())).thenReturn(signedTransaction, thriceSignedTransaction)

        //When receive finality is called
        val memberLookup = mock<MemberLookup>()
        whenever(memberLookup.lookup(eq(notaryX500))).thenReturn(BaseMemberInfo(notaryX500, listOf(notary.owningKey)))
        whenever(signingService.sign(any(), eq(notary.owningKey), any()))
            .thenReturn(toSignatureWithMetadata(notary.owningKey).signature)
        whenever(memberLookup.myInfo()).thenReturn(BaseMemberInfo(alice, listOf( publicKeys[0])))

        //When we call finality on the transaction
        val validator = mock<UtxoTransactionValidator>()
        val finalizer = UtxoTransactionFinalityHandler(
            memberLookup, signingService, signingService, persistenceService, mock())
        val finalSignedTx = finalizer.receiveFinality(flowSession, validator)

        // Then the verifier should have been called
        verify(validator, times(1)).checkTransaction(signedTransaction.toLedgerTransaction())

        // And the final signed transaction should be the one that has been signed by all parties
        assertThat(finalSignedTx, `is`(thriceSignedTransaction))

        // And it should have been persisted
        verify(persistenceService, times(1)).persist(
            (finalSignedTx as UtxoSignedTransactionBase).toEntity()
        )
    }

    @Test
    fun `should fail on contract verification failure`(){
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val signingService = mock<SigningService>()
        publicKeys.forEach {
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(toSignatureWithMetadata(it).signature)
        }

        val faultyState = TestUtxoState("Faulty State", publicKeys)
        val signedTransaction = UtxoSignedTransactionBase(
            listOf(toSignatureWithMetadata(publicKeys[0])),
            UtxoStateLedgerInfo(
                listOf(TestUtxoCommand()),
                emptyList(),
                notary,
                emptyList(),
                publicKeys,
                SimTimeWindow(Instant.now(), Instant.now().plusMillis(1.days.toMillis())),
                listOf(
                    ContractStateAndEncumbranceTag(faultyState, null),
                ),
                emptyList()
            ),
            signingService,
            serializationService,
            persistenceService,
            config
        )
        val finalizer = UtxoTransactionFinalityHandler(
            mock(), signingService, signingService, persistenceService, mock())

        assertThatThrownBy{finalizer.finalizeTransaction(signedTransaction, mock())}
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("The following contract verification requirements were not met")
    }

    @Test
    fun `should fail on faulty encumbrance with duplicate inputs`(){
        val persistenceService = mock<PersistenceService>()
        val signingService = mock<SigningService>()
        val signedTransaction = mock<UtxoSignedTransaction>()
        val ledgerTx = mock<UtxoLedgerTransaction>()
        val duplicateStateAndRefs = listOf(
                StateAndRefImpl(
                    TransactionStateImpl(
                        TestUtxoState("S1", publicKeys),
                        notary,
                        EncumbranceGroupImpl(
                            1, "some-tag"
                        )),
                    StateRef(SecureHash.parse(txIds[0]), 0)),
                StateAndRefImpl(
                    TransactionStateImpl(
                        TestUtxoState("S1", publicKeys),
                        notary,
                        EncumbranceGroupImpl(
                            1, "some-tag"
                        )),
                    StateRef(SecureHash.parse(txIds[0]), 0))
            )
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTx)
        whenever(ledgerTx.inputStateAndRefs).thenReturn(duplicateStateAndRefs)
        whenever(ledgerTx.id).thenReturn(SecureHash.parse(txIds[1]))

        val finalizer = UtxoTransactionFinalityHandler(
            mock(), signingService, signingService, persistenceService, mock())

        assertThatThrownBy { finalizer.finalizeTransaction(signedTransaction, mock())  }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Encumbrance check failed")
            .hasMessageContaining("0 is used 2 times as input!")
    }

    @Test
    fun `should fail on faulty encumbrance`(){
        val persistenceService = mock<PersistenceService>()
        val signingService = mock<SigningService>()
        val signedTransaction = mock<UtxoSignedTransaction>()
        val ledgerTx = mock<UtxoLedgerTransaction>()
        whenever(signedTransaction.toLedgerTransaction()).thenReturn(ledgerTx)
        whenever(ledgerTx.id).thenReturn(SecureHash.parse(txIds[1]))

        val finalizer = UtxoTransactionFinalityHandler(
            mock(), signingService, signingService, persistenceService, mock())

        val faultyEncumbranceStateAndRefs = listOf(
            StateAndRefImpl(
                TransactionStateImpl(
                    TestUtxoState("S1", publicKeys),
                    notary,
                    EncumbranceGroupImpl(
                        3, "some-tag"
                    )),
                StateRef(SecureHash.parse(txIds[0]), 0)),
            StateAndRefImpl(
                TransactionStateImpl(
                    TestUtxoState("S2", publicKeys),
                    notary,
                    EncumbranceGroupImpl(
                        3, "some-tag"
                    )),
                StateRef(SecureHash.parse(txIds[0]), 1))
        )

        whenever(ledgerTx.inputStateAndRefs).thenReturn(faultyEncumbranceStateAndRefs)
        assertThatThrownBy { finalizer.finalizeTransaction(signedTransaction, mock())  }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Encumbrance check failed")
            .hasMessageContaining("0 is part of encumbrance group some-tag, but only 2 " +
                    "states out of 3 encumbered states are present as inputs.")
    }

    private val txIds = listOf(
        "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
        "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9E"
    )
}