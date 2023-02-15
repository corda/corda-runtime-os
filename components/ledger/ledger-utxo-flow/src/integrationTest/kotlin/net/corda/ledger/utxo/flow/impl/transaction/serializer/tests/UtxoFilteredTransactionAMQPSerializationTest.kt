package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.createExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Suppress("FunctionName")
class UtxoFilteredTransactionAMQPSerializationTest : UtxoLedgerIntegrationTest() {

    private companion object {
        val inputHash = SecureHash.parse("SHA256:1234567890abcdef")
        val outputInfo = UtxoOutputInfoComponent(
            encumbrance = null,
            encumbranceGroupSize = null,
            notary = Party(MemberX500Name("alice", "LDN", "GB"), publicKeyExample),
            contractStateTag = UtxoStateClassExample::class.java.name,
            contractTag = "contract tag"
        )
    }

    @Test
    fun `can serialize and deserialize utxo filtered transaction with outputs audit proof`() {
        val outputState1 = UtxoStateClassExample("1", emptyList())
        val outputState2 = UtxoStateClassExample("2", emptyList())
        val utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            wireTransactionFactory,
            componentGroups = listOf(
                emptyList(), // Notary
                emptyList(), // Signatories
                listOf(
                    serializationService.serialize(outputInfo).bytes,
                    serializationService.serialize(outputInfo).bytes
                ), // output infos
                emptyList(), // command infos
                emptyList(), // attachments
                listOf(
                    serializationService.serialize(StateRef(inputHash, 0)).bytes,
                    serializationService.serialize(StateRef(inputHash, 1)).bytes
                ), // inputs
                emptyList(), // references
                listOf(
                    serializationService.serialize(outputState1).bytes,
                    serializationService.serialize(outputState2).bytes
                ), // outputs
                emptyList(), // commands
            )
        )
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withInputStates()
            .withOutputStates()
            .build()

        assertThat(utxoFilteredTransaction.id).isNotNull

        val bytes = serializationService.serialize(utxoFilteredTransaction)
        assertThat(bytes).isNotNull
        val deserialized = serializationService.deserialize(bytes, UtxoFilteredTransaction::class.java)

        // check that the deserialized UtxoFilteredTransaction is fully functional
        assertThat(deserialized).isNotNull
        assertThat(deserialized.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(deserialized.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(deserialized.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputs = deserialized.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputs.size).isEqualTo(2)
        assertThat(inputs.values.size).isEqualTo(2)
        assertThat(inputs.values[0]?.transactionId).isEqualTo(inputHash)

        assertThat(deserialized.outputStateAndRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        val outputs = deserialized.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>
        assertThat(outputs.size).isEqualTo(2)
        assertThat(outputs.values.size).isEqualTo(2)
        assertThat(outputs.values[0]?.state?.contractState).isEqualTo(outputState1)
        assertThat(outputs.values[1]?.state?.contractState).isEqualTo(outputState2)
    }

    @Test
    fun `can serialize and deserialize utxo filtered transaction with outputs size proof`() {
        val utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            wireTransactionFactory,
            componentGroups = listOf(
                emptyList(), // Notary
                emptyList(), // Signatories
                listOf(
                    serializationService.serialize(outputInfo).bytes,
                    serializationService.serialize(outputInfo).bytes
                ), // output infos
                emptyList(), // command infos
                emptyList(), // attachments
                listOf(
                    serializationService.serialize(StateRef(inputHash, 0)).bytes,
                    serializationService.serialize(StateRef(inputHash, 1)).bytes
                ), // inputs
                emptyList(), // references
                listOf(
                    serializationService.serialize(UtxoStateClassExample("1", emptyList())).bytes,
                    serializationService.serialize(UtxoStateClassExample("2", emptyList())).bytes
                ), // outputs
                emptyList(), // commands
            )
        )
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withInputStates()
            .withOutputStatesSize()
            .build()

        assertThat(utxoFilteredTransaction.id).isNotNull

        val bytes = serializationService.serialize(utxoFilteredTransaction)
        assertThat(bytes).isNotNull
        val deserialized = serializationService.deserialize(bytes, UtxoFilteredTransaction::class.java)

        // check that the deserialized UtxoFilteredTransaction is fully functional
        assertThat(deserialized).isNotNull
        assertThat(deserialized.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(deserialized.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(deserialized.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputs = deserialized.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputs.size).isEqualTo(2)
        assertThat(inputs.values.size).isEqualTo(2)
        assertThat(inputs.values[0]?.transactionId).isEqualTo(inputHash)

        assertThat(deserialized.outputStateAndRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        val outputs = deserialized.outputStateAndRefs as UtxoFilteredData.SizeOnly
        assertThat(outputs.size).isEqualTo(2)
    }

    @Test
    fun `can serialize and deserialize utxo filtered transaction with audit proof of empty list`() {
        val utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            wireTransactionFactory,
            componentGroups = listOf(
                emptyList(), // Notary
                emptyList(), // Signatories
                listOf(
                    serializationService.serialize(outputInfo).bytes,
                    serializationService.serialize(outputInfo).bytes
                ), // output infos
                emptyList(), // command infos
                emptyList(), // attachments
                emptyList(), // inputs
                emptyList(), // references
                listOf(
                    serializationService.serialize(UtxoStateClassExample("1", emptyList())).bytes,
                    serializationService.serialize(UtxoStateClassExample("2", emptyList())).bytes
                ), // outputs
                emptyList(), // commands
            )
        )
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withInputStates()
            .withOutputStatesSize()
            .build()

        assertThat(utxoFilteredTransaction.id).isNotNull

        val bytes = serializationService.serialize(utxoFilteredTransaction)
        assertThat(bytes).isNotNull
        val deserialized = serializationService.deserialize(bytes, UtxoFilteredTransaction::class.java)

        // check that the deserialized UtxoFilteredTransaction is fully functional
        assertThat(deserialized).isNotNull
        assertThat(deserialized.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(deserialized.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(deserialized.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputs = deserialized.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputs.size).isEqualTo(0)
        assertThat(inputs.values.size).isEqualTo(0)

        assertThat(deserialized.outputStateAndRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        val outputs = deserialized.outputStateAndRefs as UtxoFilteredData.SizeOnly
        assertThat(outputs.size).isEqualTo(2)
    }

}