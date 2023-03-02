package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class UtxoTransactionBuilderContainerTest : UtxoLedgerTest() {
    private val hash1 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 2)), 0)
    private val state1 = mock<ContractStateAndEncumbranceTag>()
    private val state2 = mock<ContractStateAndEncumbranceTag>()

    private val anotherPublicKey = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair().public

    private val alternativeNotary = Party(
        MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
        anotherPublicKey
    )

    @Test
    fun `minus - empty ones return empty`() {
        val result =
            UtxoTransactionBuilderContainer() -
                    UtxoTransactionBuilderContainer()
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }

    @Test
    fun `minus - notary gets set when original is null`() {
        val result =
            UtxoTransactionBuilderContainer(notary = utxoNotaryExample) -
                    UtxoTransactionBuilderContainer()
        assertEquals(utxoNotaryExample, result.getNotary())
    }

    @Test
    fun `minus - notary does gets set when original is not null`() {
        val result =
            UtxoTransactionBuilderContainer(notary = alternativeNotary) -
                    UtxoTransactionBuilderContainer(notary = utxoNotaryExample)
        assertNull(result.getNotary())
    }

    @Test
    fun `minus - time window gets set when original is null`() {
        val result =
            UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample) -
                    UtxoTransactionBuilderContainer()
        assertEquals(utxoTimeWindowExample, result.timeWindow)
    }

    @Test
    fun `minus - time window does gets set when original is not null`() {
        val result =
            UtxoTransactionBuilderContainer(timeWindow = TimeWindowUntilImpl(Instant.MAX)) -
                    UtxoTransactionBuilderContainer(timeWindow = utxoTimeWindowExample)
        assertNull(result.timeWindow)
    }

    @Test
    fun `minus - attachments does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(attachments = listOf(hash1, hash2)) -
                    UtxoTransactionBuilderContainer(attachments = listOf(hash1, hash2))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - attachments get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(attachments = listOf(hash1, hash1, hash2)) -
                    UtxoTransactionBuilderContainer(attachments = listOf(hash1))
        assertContentEquals(listOf(hash2), result.attachments)
    }

    @Test
    fun `minus - commands does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(commands = listOf(command1, command2)) -
                    UtxoTransactionBuilderContainer(commands = listOf(command1, command2))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - commands get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(commands = listOf(command1, command1, command2)) -
                    UtxoTransactionBuilderContainer(commands = listOf(command1))
        assertContentEquals(listOf(command2), result.commands)
    }

    @Test
    fun `minus - signatories does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(signatories = listOf(publicKeyExample, anotherPublicKey)) -
                    UtxoTransactionBuilderContainer(signatories = listOf(publicKeyExample, anotherPublicKey))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - signatories get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(signatories = listOf(publicKeyExample, publicKeyExample, anotherPublicKey)) -
                    UtxoTransactionBuilderContainer(signatories = listOf(publicKeyExample))
        assertContentEquals(listOf(anotherPublicKey), result.signatories)
    }

    @Test
    fun `minus - input StateRefs does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(inputStateRefs = listOf(stateRef1, stateRef2)) -
                    UtxoTransactionBuilderContainer(inputStateRefs = listOf(stateRef1, stateRef2))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - input StateRefs get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(inputStateRefs = listOf(stateRef1, stateRef1, stateRef2)) -
                    UtxoTransactionBuilderContainer(inputStateRefs = listOf(stateRef1))
        assertContentEquals(listOf(stateRef2), result.inputStateRefs)
    }

    @Test
    fun `minus - reference StateRefs does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(referenceStateRefs = listOf(stateRef1, stateRef2)) -
                    UtxoTransactionBuilderContainer(referenceStateRefs = listOf(stateRef1, stateRef2))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - reference StateRefs get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(referenceStateRefs = listOf(stateRef1, stateRef1, stateRef2)) -
                    UtxoTransactionBuilderContainer(referenceStateRefs = listOf(stateRef1))
        assertContentEquals(listOf(stateRef2), result.referenceStateRefs)
    }

    @Test
    fun `minus - outputs does not get set if they are the same`() {
        val result =
            UtxoTransactionBuilderContainer(outputStates = listOf(state1, state2)) -
                    UtxoTransactionBuilderContainer(outputStates = listOf(state1, state2))
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }


    @Test
    fun `minus - outputs get set when there is a new one`() {
        val result =
            UtxoTransactionBuilderContainer(outputStates = listOf(state1, state1, state2)) -
                    UtxoTransactionBuilderContainer(outputStates = listOf(state1))
        assertContentEquals(listOf(state2), result.outputStates)
    }
}
