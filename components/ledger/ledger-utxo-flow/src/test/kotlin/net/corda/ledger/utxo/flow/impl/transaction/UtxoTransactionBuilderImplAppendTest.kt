package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertContentEquals

class UtxoTransactionBuilderImplAppendTest : UtxoLedgerTest() {
    private lateinit var originalTransactionalBuilder: UtxoTransactionBuilderImpl

    private val hash1 = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHashImpl("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    @BeforeEach
    fun beforeEach() {
        originalTransactionalBuilder = utxoLedgerService.createTransactionBuilder()
    }

    @Test
    fun `Appending empty to empty returns an empty`() {
        val result = originalTransactionalBuilder.append(UtxoTransactionBuilderContainer())
        assertEquals(utxoLedgerService.createTransactionBuilder(), result)
    }

    @Test
    fun `Sets new notary if old is null`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                notaryName = notaryX500Name
            )
        )
        assertEquals(notaryX500Name, result.notaryName)
        assertEquals(publicKeyExample, result.notaryKey)
    }

    @Test
    fun `Does not set new notary if old exists`() {
        originalTransactionalBuilder.setNotary(notaryX500Name)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                notaryName = anotherNotaryX500Name
            )
        )
        assertEquals(notaryX500Name, result.notaryName)
        assertEquals(publicKeyExample, result.notaryKey)
    }

    @Test
    fun `Sets new time window if old is null`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                timeWindow = utxoTimeWindowExample
            )
        )
        assertEquals(utxoTimeWindowExample, result.timeWindow)
    }

    @Test
    fun `Does not set new time window if old exists`() {
        originalTransactionalBuilder.setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                timeWindow = TimeWindowUntilImpl(Instant.MAX)
            )
        )
        assertEquals(utxoTimeWindowExample, result.timeWindow)
    }

    @Test
    fun `Appends commands`() {
        originalTransactionalBuilder.addCommand(command1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                commands = listOf(command1, command1, command2)
            )
        )
        assertContentEquals(listOf(command1, command1, command1, command2), result.commands)
    }

    @Test
    fun `Does not add again already added signatories`() {
        originalTransactionalBuilder.addSignatories(publicKeyExample)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                signatories = listOf(publicKeyExample)
            )
        )
        assertContentEquals(listOf(publicKeyExample), result.signatories)
    }

    @Test
    fun `Appends and deduplicates new signatories`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                signatories = listOf(publicKeyExample, publicKeyExample, anotherPublicKeyExample)
            )
        )
        assertContentEquals(listOf(publicKeyExample, anotherPublicKeyExample), result.signatories)
    }

    @Test
    fun `Does not add again already added input StateRefs`() {
        originalTransactionalBuilder.addInputStates(stateRef1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                inputStateRefs = listOf(stateRef1)
            )
        )
        assertContentEquals(listOf(stateRef1), result.inputStateRefs)
    }

    @Test
    fun `Appends and deduplicates new input StateRefs`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                inputStateRefs = listOf(stateRef1, stateRef1, stateRef2)
            )
        )
        assertContentEquals(listOf(stateRef1, stateRef2), result.inputStateRefs)
    }

    @Test
    fun `Does not add again already added reference StateRefs`() {
        originalTransactionalBuilder.addReferenceStates(stateRef1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = listOf(stateRef1)
            )
        )
        assertContentEquals(listOf(stateRef1), result.referenceStateRefs)
    }

    @Test
    fun `Appends and deduplicates new reference StateRefs`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                referenceStateRefs = listOf(stateRef1, stateRef1, stateRef2)
            )
        )
        assertContentEquals(listOf(stateRef1, stateRef2), result.referenceStateRefs)
    }

    @Test
    fun `Appends output states`() {
        originalTransactionalBuilder.addOutputState(state1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                outputStates = listOf(stateWithEnc1, stateWithEnc1, stateWithEnc2)
            )
        )
        assertContentEquals(listOf(stateWithEnc1, stateWithEnc1, stateWithEnc1, stateWithEnc2), result.outputStates)
    }
}
