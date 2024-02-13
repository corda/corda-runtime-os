package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class UtxoBaselinedTransactionBuilderDiffTest : UtxoLedgerTest() {
    private val hash1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    @Test
    fun `diff - empty ones return empty`() {
        val result =
            UtxoBaselinedTransactionBuilder(utxoTransactionBuilder)
                .diff()
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }

    @Test
    fun `diff - notary gets set when original is null`() {
        val result =
            UtxoBaselinedTransactionBuilder(utxoTransactionBuilder)
                .setNotary(notaryX500Name)
                .diff()
        assertEquals(notaryX500Name, result.getNotaryName())
    }

    @Test
    fun `diff - notary does not get set when original is not null`() {
        val result =
            UtxoBaselinedTransactionBuilder(utxoTransactionBuilder.setNotary(notaryX500Name) as UtxoTransactionBuilderInternal)
                .diff()
        assertNull(result.getNotaryName())
    }

    @Test
    fun `diff - time window gets set when original is null`() {
        val result =
            UtxoBaselinedTransactionBuilder(utxoTransactionBuilder)
                .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
                .diff()
        assertEquals(utxoTimeWindowExample, result.timeWindow)
    }

    @Test
    fun `diff - time window does not get set when original is not null`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.setTimeWindowBetween(
                    utxoTimeWindowExample.from,
                    utxoTimeWindowExample.until
                ) as UtxoTransactionBuilderInternal
            )
                .diff()
        assertNull(result.timeWindow)
    }

    @Test
    fun `diff - commands get added regardless of previous same ones or duplications`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addCommand(command1) as UtxoTransactionBuilderInternal
            )
                .addCommand(command1)
                .addCommand(command2)
                .addCommand(command2)
                .diff()

        assertContentEquals(listOf(command1, command2, command2), result.commands)
    }

    @Test
    fun `diff - signatories do not get set if they are the same`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addSignatories(
                    publicKeyExample,
                    anotherPublicKeyExample
                ) as UtxoTransactionBuilderInternal
            ).diff()

        assertEquals(UtxoTransactionBuilderContainer(), result)
    }

    @Test
    fun `diff - signatories get set when there is a new one`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addSignatories(
                    publicKeyExample,
                ) as UtxoTransactionBuilderInternal
            ).addSignatories(anotherPublicKeyExample)
                .diff()

        assertContentEquals(listOf(anotherPublicKeyExample), result.signatories)
    }

    @Test
    fun `diff - input StateRefs do not get set if they are the same`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addInputStates(
                    stateRef1,
                    stateRef2
                ) as UtxoTransactionBuilderInternal
            ).diff()

        assertEquals(UtxoTransactionBuilderContainer(), result)
    }

    @Test
    fun `diff - input StateRefs get set when there is a new one`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addInputStates(
                    stateRef1
                ) as UtxoTransactionBuilderInternal
            ).addInputStates(
                stateRef2
            )
                .diff()
        assertContentEquals(listOf(stateRef2), result.inputStateRefs)
    }

    @Test
    fun `diff - reference StateRefs do not get set if they are the same`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addReferenceStates(
                    stateRef1,
                    stateRef2
                ) as UtxoTransactionBuilderInternal
            )
                .diff()
        assertEquals(UtxoTransactionBuilderContainer(), result)
    }

    @Test
    fun `diff - reference StateRefs get set when there is a new one`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addReferenceStates(
                    stateRef1
                ) as UtxoTransactionBuilderInternal
            ).addReferenceStates(
                stateRef2
            )
                .diff()
        assertContentEquals(listOf(stateRef2), result.referenceStateRefs)
    }

    @Test
    fun `diff - outputs get added regardless of previous same ones or duplications`() {
        val result =
            UtxoBaselinedTransactionBuilder(
                utxoTransactionBuilder.addOutputState(state1) as UtxoTransactionBuilderInternal
            )
                .addOutputState(state1)
                .addOutputState(state2)
                .addOutputState(state2)
                .diff()

        assertContentEquals(listOf(stateWithEnc1, stateWithEnc2, stateWithEnc2), result.outputStates)
    }
}
