package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.utilities.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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

    @Test
    fun `serialize and deserialize with encumbrance`() {
        val inputStateAndRef = getExampleStateAndRefImpl()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val signedTx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addEncumberedOutputStates(
                "encumbrance 1",
                UtxoStateClassExample("test 1", listOf(publicKeyExample)),
                UtxoStateClassExample("test 2", listOf(publicKeyExample))
            )
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .toSignedTransaction()

        val bytes = serializationService.serialize(signedTx)
        val deserialized = serializationService.deserialize(bytes)

        assertThat(deserialized).isEqualTo(signedTx)
        assertThat(deserialized.outputStateAndRefs).hasSize(3)

        assertThat(deserialized.outputStateAndRefs[0].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbrance 1")
        assertThat(deserialized.outputStateAndRefs[0].state.encumbranceGroup).isNotNull.extracting { it?.size }
            .isEqualTo(2)

        assertThat(deserialized.outputStateAndRefs[1].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbrance 1")
        assertThat(deserialized.outputStateAndRefs[1].state.encumbranceGroup).isNotNull.extracting { it?.size }
            .isEqualTo(2)

        assertThat(deserialized.outputStateAndRefs[2].state.encumbranceGroup).isNull()
    }
}