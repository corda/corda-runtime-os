package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.v5.ledger.common.Party
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
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withNotaryAndTimeWindow().notary
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.NOTARY.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<Party>).predicate.test(mock()))
            .isEqualTo(true)
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
    fun withReferenceInputStatesSize() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceInputStatesSize().referenceInputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.REFERENCES.ordinal)
    }

    @Test
    fun withReferenceInputStates() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceInputStates().referenceInputStates
        assertThat(componentGroupFilterParameters).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat((componentGroupFilterParameters!!).componentGroupIndex).isEqualTo(UtxoComponentGroup.REFERENCES.ordinal)
        assertThat((componentGroupFilterParameters as ComponentGroupFilterParameters.AuditProof<StateRef>).predicate.test(mock()))
            .isEqualTo(true)
    }

    @Test
    fun `withReferenceInputStates predicate`() {
        val componentGroupFilterParameters = utxoFilteredTransactionBuilder.withReferenceInputStates { false }.referenceInputStates
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
    fun `all start as null`() {
        assertThat(utxoFilteredTransactionBuilder.notary).isNull()
        assertThat(utxoFilteredTransactionBuilder.signatories).isNull()
        assertThat(utxoFilteredTransactionBuilder.inputStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.referenceInputStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.outputStates).isNull()
        assertThat(utxoFilteredTransactionBuilder.commands).isNull()
    }

    @Test
    fun `set all`() {
        val builder = utxoFilteredTransactionBuilder
            .withNotaryAndTimeWindow()
            .withSignatoriesSize()
            .withInputStates()
            .withReferenceInputStatesSize()
            .withOutputStatesSize()
            .withCommands() as UtxoFilteredTransactionBuilderInternal
        assertThat(builder.notary).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat(builder.signatories).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.inputStates).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat(builder.referenceInputStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.outputStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.commands).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
    }

    @Test
    fun `miss some`() {
        val builder = utxoFilteredTransactionBuilder
            .withInputStates()
            .withReferenceInputStatesSize()
            .withCommands() as UtxoFilteredTransactionBuilderInternal
        assertThat(builder.notary).isNull()
        assertThat(builder.signatories).isNull()
        assertThat(builder.inputStates).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
        assertThat(builder.referenceInputStates).isInstanceOf(ComponentGroupFilterParameters.SizeProof::class.java)
        assertThat(builder.outputStates).isNull()
        assertThat(builder.commands).isInstanceOf(ComponentGroupFilterParameters.AuditProof::class.java)
    }
}