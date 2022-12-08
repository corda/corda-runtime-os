package net.corda.ledger.utxo.flow.impl.transaction.filtered.factory

import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionBuilderInternal
import net.corda.ledger.utxo.test.UtxoLedgerTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UtxoFilteredTransactionFactoryImplTest : UtxoLedgerTest() {

    private val filteredTransactionFactory = mock<FilteredTransactionFactory>()
    private val utxoFilteredTransactionFactory = UtxoFilteredTransactionFactoryImpl(filteredTransactionFactory, mock())

    private val signedTransaction = mock<UtxoSignedTransactionInternal>()
    private val argumentCaptor = argumentCaptor<List<ComponentGroupFilterParameters>>()

    @BeforeEach
    fun beforeEach() {
        whenever(signedTransaction.wireTransaction).thenReturn(wireTransactionExample)
        whenever(filteredTransactionFactory.create(any(), argumentCaptor.capture())).thenReturn(mock())
    }

    @Test
    fun `creates a filtered transaction`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withNotary()
            .withSignatories()
            .withInputStates()
        utxoFilteredTransactionFactory.create(signedTransaction, builder as UtxoFilteredTransactionBuilderInternal)
        verify(filteredTransactionFactory).create(any(), any())
    }

    @Test
    fun `includes the component groups set on the filtered transaction builder`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withNotary()
            .withSignatories()
            .withInputStates()
        utxoFilteredTransactionFactory.create(signedTransaction, builder as UtxoFilteredTransactionBuilderInternal)

        val componentGroupFilterParameters = argumentCaptor.firstValue
        val componentGroups = componentGroupFilterParameters.map { it.componentGroupIndex }
        assertThat(componentGroupFilterParameters).hasSize(4)
        assertThat(componentGroups).containsExactly(
            UtxoComponentGroup.METADATA.ordinal,
            UtxoComponentGroup.NOTARY.ordinal,
            UtxoComponentGroup.SIGNATORIES.ordinal,
            UtxoComponentGroup.INPUTS.ordinal
        )
    }

    @Test
    fun `includes output state infos if the output state component group is included`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withOutputStates()
        utxoFilteredTransactionFactory.create(signedTransaction, builder)

        val componentGroupFilterParameters = argumentCaptor.firstValue
        val componentGroups = componentGroupFilterParameters.map { it.componentGroupIndex }
        assertThat(componentGroupFilterParameters).hasSize(3)
        assertThat(componentGroups).containsExactly(
            UtxoComponentGroup.METADATA.ordinal,
            UtxoComponentGroup.OUTPUTS_INFO.ordinal,
            UtxoComponentGroup.OUTPUTS.ordinal
        )
    }

    @Test
    fun `does not include output state infos if the output state component group is not included`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withInputStates()
        utxoFilteredTransactionFactory.create(signedTransaction, builder)

        val componentGroupFilterParameters = argumentCaptor.firstValue
        val componentGroups = componentGroupFilterParameters.map { it.componentGroupIndex }
        assertThat(componentGroups).doesNotContain(UtxoComponentGroup.OUTPUTS.ordinal, UtxoComponentGroup.OUTPUTS_INFO.ordinal)
    }

    @Test
    fun `includes command infos if the commands component group is included`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withCommands()
        utxoFilteredTransactionFactory.create(signedTransaction, builder)

        val componentGroupFilterParameters = argumentCaptor.firstValue
        val componentGroups = componentGroupFilterParameters.map { it.componentGroupIndex }
        assertThat(componentGroupFilterParameters).hasSize(3)
        assertThat(componentGroups).containsExactly(
            UtxoComponentGroup.METADATA.ordinal,
            UtxoComponentGroup.COMMANDS_INFO.ordinal,
            UtxoComponentGroup.COMMANDS.ordinal
        )
    }

    @Test
    fun `does not include command infos if the commands component group is not included`() {
        val builder = UtxoFilteredTransactionBuilderImpl(utxoFilteredTransactionFactory, mock())
            .withInputStates()
        utxoFilteredTransactionFactory.create(signedTransaction, builder)

        val componentGroupFilterParameters = argumentCaptor.firstValue
        val componentGroups = componentGroupFilterParameters.map { it.componentGroupIndex }
        assertThat(componentGroups).doesNotContain(UtxoComponentGroup.COMMANDS.ordinal, UtxoComponentGroup.COMMANDS_INFO.ordinal)
    }
}