package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.PublicKey

class UtxoFilteredTransactionImplTest : UtxoFilteredTransactionTestBase() {

    @Test
    fun `metada and id are always present`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ) { true }
            )
        )

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTx.id).isEqualTo(wireTransaction.id)
        assertThat(utxoFilteredTx.metadata.getLedgerModel())
            .isEqualTo(wireTransaction.metadata.getLedgerModel())

    }

    @Test
    fun `Can reconstruct filtered output`() {
        assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS_INFO.ordinal)).hasSize(2)
        assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS.ordinal)).hasSize(2)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTx.outputStateAndRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val outputs = utxoFilteredTx.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>

        assertThat(outputs.size).isEqualTo(2)
        assertThat(outputs.values.size).isEqualTo(2)
        assertThat(outputs.values[0]?.state?.contractState).isInstanceOf(OutputState1::class.java)
        assertThat(outputs.values[1]?.state?.contractState).isInstanceOf(OutputState2::class.java)
    }

    @Test
    fun `can fetch input state refs`() {
        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputStateRefs.size).isEqualTo(2)
        assertThat(inputStateRefs.values.keys.first()).isEqualTo(0)
        assertThat(inputStateRefs.values[0]?.transactionHash).isEqualTo(inputId)
        assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        assertThat(inputStateRefs.values[0]?.index).isEqualTo(0)
        assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }

    @Test
    fun `can filter input state refs`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ) { true },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java) { it.index != 0 },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java) { it.index != 0 },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        )

        assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.INPUTS.ordinal))
            .hasSize(1)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputStateRefs.size).isEqualTo(2)
        assertThat(inputStateRefs.values.size).isEqualTo(1)
        assertThat(inputStateRefs.values.keys.first()).isEqualTo(1)
        assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }


    @Test
    fun `can get notary and time window`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTransaction.notary).isEqualTo(notary)
        assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary but not time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ) { true },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java) {
                    when (it) {
                        is Party -> false
                        else -> true
                    }
                },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        )
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTransaction.notary).isNull()
        assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary and time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java) { true },
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ) { true },
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        )
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTransaction.notary).isNull()
        assertThat(utxoFilteredTransaction.timeWindow).isNull()
    }

    @Test
    fun `can get the number of commmands but no data`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTransaction.commands)
            .isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        val commands = utxoFilteredTransaction.commands as UtxoFilteredData.SizeOnly
        assertThat(commands.size).isEqualTo(1)
    }

    @Test
    fun `cannot find out about reference states`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        assertThat(utxoFilteredTransaction.referenceStateRefs)
            .isInstanceOf(UtxoFilteredData.Removed::class.java)

    }
}

class OutputState1 : ContractState {
    override val participants: List<PublicKey>
        get() = TODO("Not yet implemented")
}

class OutputState2 : ContractState {
    override val participants: List<PublicKey>
        get() = TODO("Not yet implemented")
}
