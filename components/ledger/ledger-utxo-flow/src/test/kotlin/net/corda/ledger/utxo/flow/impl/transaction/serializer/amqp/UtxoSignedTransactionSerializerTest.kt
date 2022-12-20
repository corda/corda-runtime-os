package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class UtxoSignedTransactionSerializerTest : UtxoLedgerTest() {
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
        it.register(utxoSignedTransactionAMQPSerializer, it)
    }, cipherSchemeMetadata)

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val bytes = serializationService.serialize(utxoSignedTransactionExample)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(utxoSignedTransactionExample, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(utxoSignedTransactionExample.id, deserialized.id)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `serialize and deserialize with encumbrance`() {
        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateRef = referenceStateAndRef.ref

        val mockSignedTxForInput = mock<UtxoSignedTransaction>()
        val mockSignedTxForRef = mock<UtxoSignedTransaction>()

        whenever(mockSignedTxForInput.outputStateAndRefs).thenReturn(listOf(inputStateAndRef))
        whenever(mockSignedTxForRef.outputStateAndRefs).thenReturn(listOf(referenceStateAndRef))
        whenever(mockUtxoLedgerPersistenceService.find(any(), any()))
            .thenReturn(mockSignedTxForInput)
            .thenReturn(mockSignedTxForRef)

        val signedTx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addEncumberedOutputStates(
                "encumbrance 1",
                UtxoStateClassExample("test 1", listOf(publicKeyExample)),
                UtxoStateClassExample("test 2", listOf(publicKeyExample))
            )
            .addOutputState(utxoStateExample)
            .addInputState(inputStateRef)
            .addReferenceInputState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .toSignedTransaction(publicKeyExample)

        val bytes = serializationService.serialize(signedTx)
        val deserialized = serializationService.deserialize(bytes)

        assertThat(deserialized).isEqualTo(signedTx)
        assertThat(deserialized.outputStateAndRefs).hasSize(3)

        assertThat(deserialized.outputStateAndRefs[0].state.encumbrance).isNotNull().extracting { it?.tag }
            .isEqualTo("encumbrance 1")
        assertThat(deserialized.outputStateAndRefs[0].state.encumbrance).isNotNull().extracting { it?.size }
            .isEqualTo(2)

        assertThat(deserialized.outputStateAndRefs[1].state.encumbrance).isNotNull().extracting { it?.tag }
            .isEqualTo("encumbrance 1")
        assertThat(deserialized.outputStateAndRefs[1].state.encumbrance).isNotNull().extracting { it?.size }
            .isEqualTo(2)

        assertThat(deserialized.outputStateAndRefs[2].state.encumbrance).isNull()
    }
}