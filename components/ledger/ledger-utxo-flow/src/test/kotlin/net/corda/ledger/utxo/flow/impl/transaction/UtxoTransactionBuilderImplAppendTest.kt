package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowUntilImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlin.test.assertContentEquals

class UtxoTransactionBuilderImplAppendTest : UtxoLedgerTest() {
    private lateinit var originalTransactionalBuilder: UtxoTransactionBuilderImpl

    private val hash1 = SecureHash("SHA", byteArrayOf(1, 1, 1, 1))
    private val hash2 = SecureHash("SHA", byteArrayOf(2, 2, 2, 2))
    private val command1 = UtxoCommandExample("command 1")
    private val command2 = UtxoCommandExample("command 2")
    private val stateRef1 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHash("SHA", byteArrayOf(1, 1, 2, 2)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val stateWithEnc1 = ContractStateAndEncumbranceTag(state1, null)
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val stateWithEnc2 = ContractStateAndEncumbranceTag(state2, null)

    private val anotherPublicKey = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair().public

    @BeforeEach
    fun beforeEach() {
        originalTransactionalBuilder = utxoLedgerService.transactionBuilder
    }

    @Test
    fun `Appending empty to empty returns an empty`() {
        val result = originalTransactionalBuilder.append(UtxoTransactionBuilderContainer())
        assertEquals(utxoLedgerService.transactionBuilder, result)
    }

    @Test
    fun `Sets new notary if old is null`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                notary = utxoNotaryExample
            )
        )
        assertEquals(utxoNotaryExample, result.notary)
    }

    @Test
    fun `Does not set new notary if old exists`() {
        val alternativeNotary = Party(
            MemberX500Name.parse("O=AnotherExampleNotaryService, L=London, C=GB"),
            anotherPublicKey
        )
        originalTransactionalBuilder.setNotary(utxoNotaryExample)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                notary = alternativeNotary
            )
        )
        assertEquals(utxoNotaryExample, result.notary)
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
    fun `Does not add again already added attachments`() {
        originalTransactionalBuilder.addAttachment(hash1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                attachments = listOf(hash1)
            )
        )
        assertContentEquals(listOf(hash1), result.attachments)
    }

    @Test
    fun `Appends and deduplicates new attachments`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                attachments = listOf(hash1, hash1, hash2)
            )
        )
        assertContentEquals(listOf(hash1, hash2), result.attachments)
    }

    @Test
    fun `Does not add again already added commands`() {
        originalTransactionalBuilder.addCommand(command1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                commands = listOf(command1)
            )
        )
        assertContentEquals(listOf(command1), result.commands)
    }

    @Test
    fun `Appends and deduplicates new commands`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                commands = listOf(command1, command1, command2)
            )
        )
        assertContentEquals(listOf(command1, command2), result.commands)
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
                signatories = listOf(publicKeyExample, publicKeyExample, anotherPublicKey)
            )
        )
        assertContentEquals(listOf(publicKeyExample, anotherPublicKey), result.signatories)
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
    fun `Does not add again already added output state`() {
        originalTransactionalBuilder.addOutputState(state1)
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                outputStates = listOf(stateWithEnc1)
            )
        )
        assertContentEquals(listOf(stateWithEnc1), result.outputStates)
    }

    @Test
    fun `Appends and deduplicates new output states`() {
        val result = originalTransactionalBuilder.append(
            UtxoTransactionBuilderContainer(
                outputStates = listOf(stateWithEnc1, stateWithEnc1, stateWithEnc2)
            )
        )
        assertContentEquals(listOf(stateWithEnc1, stateWithEnc2), result.outputStates)
    }
}