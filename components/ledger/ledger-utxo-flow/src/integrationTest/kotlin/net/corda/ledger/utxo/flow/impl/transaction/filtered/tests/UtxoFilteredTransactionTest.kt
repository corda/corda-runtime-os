package net.corda.ledger.utxo.flow.impl.transaction.filtered.tests

import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.timewindow.TimeWindowFromImpl
import net.corda.ledger.utxo.flow.impl.transaction.serializer.tests.UtxoFilteredTransactionAMQPSerializationTest
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.createExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.PublicKey
import java.time.Instant

class UtxoFilteredTransactionTest : UtxoLedgerIntegrationTest() {
    
    @BeforeEach
    fun beforeEach() {
        utxoSignedTransaction = createSignedTransaction()
    }
    
    @Test
    fun `create filtered transaction with all components included`() {
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

        assertThat(utxoFilteredTransaction.metadata).isEqualTo(utxoSignedTransaction.metadata)

        assertThat(utxoFilteredTransaction.notary).isEqualTo(utxoSignedTransaction.notary)

        assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(utxoSignedTransaction.timeWindow)

        assertThat(utxoFilteredTransaction.signatories).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.signatories as UtxoFilteredData.Audit<PublicKey>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.signatories)

        assertThat(utxoFilteredTransaction.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.inputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.inputStateRefs)

        assertThat(utxoFilteredTransaction.referenceInputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.referenceInputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.referenceStateRefs)

        assertThat(utxoFilteredTransaction.outputStateAndRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.outputStateAndRefs)

        assertThat(utxoFilteredTransaction.commands).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.commands as UtxoFilteredData.Audit<Command>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.commands)
    }

    @Test
    fun `create filtered transaction with no components included`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .build()

        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)
        assertThat(utxoFilteredTransaction.metadata).isEqualTo(utxoSignedTransaction.metadata)
        assertThat(utxoFilteredTransaction.notary).isNull()
        assertThat(utxoFilteredTransaction.timeWindow).isNull()
        assertThat(utxoFilteredTransaction.signatories).isInstanceOf(UtxoFilteredData.Removed::class.java)
        assertThat(utxoFilteredTransaction.inputStateRefs).isInstanceOf(UtxoFilteredData.Removed::class.java)
        assertThat(utxoFilteredTransaction.referenceInputStateRefs).isInstanceOf(UtxoFilteredData.Removed::class.java)
        assertThat(utxoFilteredTransaction.outputStateAndRefs).isInstanceOf(UtxoFilteredData.Removed::class.java)
        assertThat(utxoFilteredTransaction.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)
    }

    @Test
    fun `create filtered transaction some components included`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withNotary()
            .withSignatories()
            .withInputStates()
            .withOutputStates()
            .build()

        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(utxoFilteredTransaction.metadata).isEqualTo(utxoSignedTransaction.metadata)

        assertThat(utxoFilteredTransaction.notary).isEqualTo(utxoSignedTransaction.notary)

        assertThat(utxoFilteredTransaction.timeWindow).isNull()

        assertThat(utxoFilteredTransaction.signatories).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.signatories as UtxoFilteredData.Audit<PublicKey>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.signatories)

        assertThat(utxoFilteredTransaction.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.inputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.inputStateRefs)

        assertThat(utxoFilteredTransaction.referenceInputStateRefs).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(utxoFilteredTransaction.outputStateAndRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.outputStateAndRefs)

        assertThat(utxoFilteredTransaction.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)
    }

    @Test
    fun `create filtered transaction without notary or time window`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .build()
        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)
        assertThat(utxoFilteredTransaction.notary).isNull()
        assertThat(utxoFilteredTransaction.timeWindow).isNull()
    }

    @Test
    fun `create filtered transaction with notary and no time window`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withNotary()
            .build()
        assertThat(utxoFilteredTransaction.notary).isEqualTo(utxoSignedTransaction.notary)
        assertThat(utxoFilteredTransaction.timeWindow).isNull()
    }

    @Test
    fun `create filtered transaction with time window and no notary`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withTimeWindow()
            .build()
        assertThat(utxoFilteredTransaction.notary).isNull()
        assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(utxoFilteredTransaction.timeWindow)
    }

    @Test
    fun `create filtered transaction with size proofs`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withNotary()
            .withTimeWindow()
            .withSignatoriesSize()
            .withInputStatesSize()
            .withReferenceInputStatesSize()
            .withOutputStatesSize()
            .withCommandsSize()
            .build()

        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(utxoFilteredTransaction.metadata).isEqualTo(utxoSignedTransaction.metadata)

        assertThat(utxoFilteredTransaction.notary).isEqualTo(utxoSignedTransaction.notary)

        assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(utxoSignedTransaction.timeWindow)

        assertThat(utxoFilteredTransaction.signatories).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.signatories as UtxoFilteredData.SizeOnly<PublicKey>).size)
            .isEqualTo(utxoSignedTransaction.signatories.size)

        assertThat(utxoFilteredTransaction.inputStateRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.inputStateRefs as UtxoFilteredData.SizeOnly<StateRef>).size)
            .isEqualTo(utxoSignedTransaction.inputStateRefs.size)

        assertThat(utxoFilteredTransaction.referenceInputStateRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.referenceInputStateRefs as UtxoFilteredData.SizeOnly<StateRef>).size)
            .isEqualTo(utxoSignedTransaction.referenceStateRefs.size)

        assertThat(utxoFilteredTransaction.outputStateAndRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.outputStateAndRefs as UtxoFilteredData.SizeOnly<StateAndRef<*>>).size)
            .isEqualTo(utxoSignedTransaction.outputStateAndRefs.size)

        assertThat(utxoFilteredTransaction.commands).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.commands as UtxoFilteredData.SizeOnly<Command>).size)
            .isEqualTo(utxoSignedTransaction.commands.size)
    }

    @Test
    fun `create filtered transaction with audit and size proofs and missing components`() {
        val utxoFilteredTransaction = utxoLedgerService.filterSignedTransaction(utxoSignedTransaction)
            .withNotary()
            .withSignatoriesSize()
            .withInputStates()
            .withOutputStates()
            .withCommandsSize()
            .build()

        assertThat(utxoFilteredTransaction.id).isEqualTo(utxoSignedTransaction.id)

        assertThat(utxoFilteredTransaction.metadata).isEqualTo(utxoSignedTransaction.metadata)

        assertThat(utxoFilteredTransaction.notary).isEqualTo(utxoSignedTransaction.notary)

        assertThat(utxoFilteredTransaction.timeWindow).isNull()

        assertThat(utxoFilteredTransaction.signatories).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.signatories as UtxoFilteredData.SizeOnly<PublicKey>).size)
            .isEqualTo(utxoSignedTransaction.signatories.size)

        assertThat(utxoFilteredTransaction.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.inputStateRefs as UtxoFilteredData.Audit<StateRef>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.inputStateRefs)

        assertThat(utxoFilteredTransaction.referenceInputStateRefs).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(utxoFilteredTransaction.outputStateAndRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        assertThat((utxoFilteredTransaction.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>).values.values)
            .containsExactlyElementsOf(utxoSignedTransaction.outputStateAndRefs)

        assertThat(utxoFilteredTransaction.commands).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        assertThat((utxoFilteredTransaction.commands as UtxoFilteredData.SizeOnly<Command>).size)
            .isEqualTo(utxoSignedTransaction.commands.size)
    }
    
    private fun createSignedTransaction(): UtxoSignedTransaction {
        val inputHash = SecureHash.parse("SHA256:1234567890abcdef")
        val outputInfo = UtxoOutputInfoComponent(
            encumbrance = null,
            notary = utxoNotaryExample,
            contractStateTag = UtxoStateClassExample::class.java.name,
            contractTag = "contract tag"
        )
        return utxoSignedTransactionFactory.createExample(
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
                    serializationService.serialize(UtxoStateClassExample("1", emptyList())).bytes,
                    serializationService.serialize(UtxoStateClassExample("2", emptyList())).bytes
                ),
                // commands
                listOf(
                    serializationService.serialize(MyCommand("1")).bytes,
                    serializationService.serialize(MyCommand("2")).bytes
                ),
            )
        )
    }

    data class MyCommand(val property: String) : Command
}