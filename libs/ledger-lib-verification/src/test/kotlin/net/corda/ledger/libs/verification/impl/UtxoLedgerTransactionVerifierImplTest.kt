package net.corda.ledger.libs.verification.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TransactionState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

class UtxoLedgerTransactionVerifierImplTest {

    private val transaction = mock<UtxoLedgerTransaction>()
    private val signatory = mock<PublicKey>()
    private val command = mock<Command>()
    private val state = mock<ContractState>()
    private val stateRef = mock<StateRef>()
    private val inputTransactionState = mock<TransactionState<ContractState>>()
    private val referenceTransactionState = mock<TransactionState<ContractState>>()
    private val metadata = mock<TransactionMetadata>()

    private val verifier = UtxoLedgerTransactionVerifierImpl({ transaction }, transaction) {
        // NO-OP contract verification
    }

    private companion object {
        val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
        val anotherNotaryX500Name = MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB")

        val kpg = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }

        val keyPairExample: KeyPair = kpg.generateKeyPair()
        val publicKeyExample: PublicKey = keyPairExample.public
        val anotherPublicKeyExample: PublicKey = kpg
            .generateKeyPair().public
    }

    @BeforeEach
    fun beforeEach() {
        whenever(transaction.id).thenReturn(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)))
        whenever(transaction.signatories).thenReturn(listOf(signatory))
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transaction.outputContractStates).thenReturn(listOf(state))
        whenever(transaction.inputTransactionStates).thenReturn(listOf(inputTransactionState))
        whenever(transaction.referenceTransactionStates).thenReturn(listOf(referenceTransactionState))
        whenever(transaction.commands).thenReturn(listOf(command))
        whenever(transaction.notaryName).thenReturn(notaryX500Name)
        whenever(transaction.notaryKey).thenReturn(publicKeyExample)
        whenever(transaction.metadata).thenReturn(metadata)

        whenever(inputTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(inputTransactionState.notaryKey).thenReturn(publicKeyExample)
        whenever(referenceTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(referenceTransactionState.notaryKey).thenReturn(publicKeyExample)
    }

    @Test
    fun `a valid transaction does not throw an exception`() {
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no signatories`() {
        whenever(transaction.signatories).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one signatory")
    }

    @Test
    fun `throws an exception when there are no input and output states`() {
        whenever(transaction.inputStateRefs).thenReturn(emptyList())
        whenever(transaction.outputContractStates).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one input state, or one output state")
    }

    @Test
    fun `does not throw an exception when there are input states but no output states`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef))
        whenever(transaction.outputContractStates).thenReturn(emptyList())
        verifier.verify()
    }

    @Test
    fun `does not throw an exception when there are output states but no input states`() {
        whenever(transaction.inputStateRefs).thenReturn(emptyList())
        whenever(transaction.outputContractStates).thenReturn(listOf(state))
        verifier.verify()
    }

    @Test
    fun `throws an exception if there are no commands`() {
        whenever(transaction.commands).thenReturn(emptyList())
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one command")
    }

    @Test
    fun `throws an exception if the same input state appears twice`() {
        whenever(transaction.inputStateRefs).thenReturn(listOf(stateRef, stateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Duplicate input states detected")
    }

    @Test
    fun `throws an exception if the same reference state appears twice`() {
        val referenceStateRef = mock<StateRef>()

        whenever(transaction.referenceStateRefs).thenReturn(listOf(referenceStateRef, referenceStateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Duplicate reference states detected")
    }

    @Test
    fun `throws an exception if there are overlapping input and reference states`() {
        whenever(transaction.referenceStateRefs).thenReturn(listOf(stateRef))
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cannot be both an input and a reference input in the same transaction.")
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary (names are different)`() {
        whenever(inputTransactionState.notaryName).thenReturn(notaryX500Name)
        whenever(referenceTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same.")
    }

    @Test
    fun `does not throw when input and reference states don't have the same notary keys (but the names are still the same)`() {
        whenever(inputTransactionState.notaryKey).thenReturn(publicKeyExample)
        whenever(referenceTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        assertDoesNotThrow { verifier.verify() }
    }

    @Test
    fun `throws an exception when input and reference states don't have the same notary passed into verification (names are different)`() {
        whenever(inputTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        whenever(referenceTransactionState.notaryName).thenReturn(anotherNotaryX500Name)
        assertThatThrownBy { verifier.verify() }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Input and reference states' notaries need to be the same as the transaction's notary")
    }

    @Test
    fun `does not throw when input and reference states have different notary keys passed into verification (names are the same)`() {
        whenever(inputTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        whenever(referenceTransactionState.notaryKey).thenReturn(anotherPublicKeyExample)
        assertDoesNotThrow { verifier.verify() }
    }

    @Test
    fun `throws an exception if input states are older than output states`() {
        // TODO CORE-8957 (needs to access the previous transactions from the backchain somehow)
    }
}
