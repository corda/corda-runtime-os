package net.corda.ledger.utxo.flow.impl.transaction.filtered.tests

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowFromImpl
import net.corda.ledger.utxo.flow.impl.transaction.serializer.tests.UtxoFilteredTransactionAMQPSerializationTest
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.createExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.PublicKey
import java.time.Instant

class UtxoFilteredTransactionTest : UtxoLedgerIntegrationTest() {

    private companion object {
        val inputHash = SecureHash.parse("SHA256:1234567890abcdef")
        val outputInfo = UtxoOutputInfoComponent(
            encumbrance = null,
            notary = utxoNotaryExample,
            contractStateTag = UtxoStateClassExample::class.java.name,
            contractTag = "contract tag"
        )
    }

    @Test
    fun `create filtered transaction with all components included`() {
        val outputState1 = UtxoFilteredTransactionAMQPSerializationTest.MyState(0)
        val outputState2 = UtxoFilteredTransactionAMQPSerializationTest.MyState(1)
        val command1 = MyCommand("1")
        val command2 = MyCommand("2")
        val utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            wireTransactionFactory,
            componentGroups = listOf(
                // Notary
                listOf(
                    serializationService.serialize(utxoNotaryExample).bytes,
                    serializationService.serialize(TimeWindowFromImpl(Instant.now())).bytes
                ),
                // Signatories
                listOf(serializationService.serialize(publicKeyExample).bytes),
                // output infos
                listOf(
                    serializationService.serialize(outputInfo).bytes,
                    serializationService.serialize(outputInfo).bytes
                ),
                // command infos
                listOf(
                    serializationService.serialize(listOf(MyCommand::class.java.name)).bytes,
                    serializationService.serialize(listOf(MyCommand::class.java.name)).bytes
                ),
                // attachments
                emptyList(),
                // inputs
                listOf(
                    serializationService.serialize(StateRef(inputHash, 0)).bytes,
                    serializationService.serialize(StateRef(inputHash, 1)).bytes
                ),
                // references
                listOf(
                    serializationService.serialize(StateRef(inputHash, 0)).bytes,
                    serializationService.serialize(StateRef(inputHash, 1)).bytes
                ),
                // outputs
                listOf(
                    serializationService.serialize(outputState1).bytes,
                    serializationService.serialize(outputState2).bytes
                ),
                // commands
                listOf(
                    serializationService.serialize(command1).bytes,
                    serializationService.serialize(command2).bytes
                ),
            )
        )
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withNotary()
            .withTimeWindow()
            .withSignatories()
            .withInputStates()
            .withReferenceInputStates()
            .withOutputStates()
            .withCommands()
            .build()

        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(utxoFilteredTransaction.metadata)
            .isEqualTo(utxoSignedTransaction.metadata)

        assertThat(utxoFilteredTransaction.notary)
            .isEqualTo(utxoSignedTransaction.notary)

        assertThat(utxoFilteredTransaction.timeWindow)
            .isEqualTo(utxoSignedTransaction.timeWindow)

        assertThat(utxoFilteredTransaction.signatories)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.signatories as UtxoFilteredData.Audit<PublicKey>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.signatories)

        assertThat(utxoFilteredTransaction.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.inputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.inputStateRefs)

        assertThat(utxoFilteredTransaction.referenceInputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.referenceInputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.referenceStateRefs)

        assertThat(utxoFilteredTransaction.outputStateAndRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.outputStateAndRefs)

        assertThat(utxoFilteredTransaction.commands)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.commands as UtxoFilteredData.Audit<Command>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.commands)
    }

    data class MyCommand(val property: String) : Command
}