package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.security.PublicKey

@Suppress("UNCHECKED_CAST")
class UtxoFilteredTransactionBuilderImplTest {

    private val utxoFilteredTransactionBuilder = UtxoFilteredTransactionBuilderImpl(mock(), mock())

    @Test
    fun withNotary() {
        assertThat(utxoFilteredTransactionBuilder.withNotary().notary).isTrue
    }

    @Test
    fun withTimeWindow() {
        assertThat(utxoFilteredTransactionBuilder.withTimeWindow().timeWindow).isTrue
    }

    @Test
    fun withSignatoriesSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withSignatoriesSize().signatories
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.SIGNATORIES.ordinal)
    }

    @Test
    fun withSignatories() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withSignatories().signatories
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.SIGNATORIES.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<PublicKey>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withSignatories predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withSignatories { false }.signatories
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.SIGNATORIES.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<PublicKey>).predicate.test(mock()))
            .isEqualTo(false)
    }

    @Test
    fun withInputStatesSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withInputStatesSize().inputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.INPUTS.ordinal)
    }

    @Test
    fun withInputStates() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withInputStates().inputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.INPUTS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<StateRef>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withInputStates predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withInputStates { false }.inputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.INPUTS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<StateRef>).predicate.test(mock()))
            .isEqualTo(false)
    }

    @Test
    fun withReferenceStatesSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceStatesSize().referenceStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.REFERENCES.ordinal)
    }

    @Test
    fun withReferenceStates() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceStates().referenceStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.REFERENCES.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<StateRef>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withReferenceStates predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceStates { false }.referenceStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.REFERENCES.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<StateRef>).predicate.test(mock()))
            .isEqualTo(false)
    }

    @Test
    fun withOutputStatesSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withOutputStatesSize().outputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.OUTPUTS.ordinal)
    }

    @Test
    fun withOutputStates() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withOutputStates().outputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.OUTPUTS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<ContractState>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withOutputStates predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withOutputStates { false }.outputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.OUTPUTS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<ContractState>).predicate.test(mock()))
            .isEqualTo(false)
    }

    @Test
    fun withCommandsSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withCommandsSize().commands
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.COMMANDS.ordinal)
    }

    @Test
    fun withCommands() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withCommands().commands
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.COMMANDS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<Command>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withCommands predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withCommands { false }.commands
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.COMMANDS.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<Command>).predicate.test(mock()))
            .isEqualTo(false)
    }

    @Test
    fun `all start as null or false`() {
        assertThat(utxoFilteredTransactionBuilder.notary).isFalse
        assertThat(utxoFilteredTransactionBuilder.timeWindow).isFalse
        assertThat(utxoFilteredTransactionBuilder.signatories).isNull()
        assertThat(utxoFilteredTransactionBuilder.inputStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.referenceStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.outputStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.commands).isNull()
    }

    @Test
    fun `set all`() {
        val builder = utxoFilteredTransactionBuilder
            .withNotary()
            .withTimeWindow()
            .withSignatoriesSize()
            .withInputStates()
            .withReferenceStatesSize()
            .withOutputStatesSize()
            .withCommands() as UtxoFilteredTransactionBuilderInternal
        assertThat(builder.notary).isTrue
        assertThat(builder.timeWindow).isTrue
        assertThat(builder.signatories).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.inputStates).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat(builder.referenceStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.outputStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.commands).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
    }

    @Test
    fun `miss some`() {
        val builder = utxoFilteredTransactionBuilder
            .withInputStates()
            .withReferenceStatesSize()
            .withCommands() as UtxoFilteredTransactionBuilderInternal
        assertThat(builder.notary).isFalse
        assertThat(builder.timeWindow).isFalse
        assertThat(builder.signatories).isNull()
        assertThat(builder.inputStates).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat(builder.referenceStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.outputStates).isNull()
        assertThat(builder.commands).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
    }
}