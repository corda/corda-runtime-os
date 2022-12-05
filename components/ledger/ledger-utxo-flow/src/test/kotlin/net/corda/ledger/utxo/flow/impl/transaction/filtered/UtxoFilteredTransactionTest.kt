package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.security.PublicKey

class UtxoFilteredTransactionTest :UtxoFilteredTransactionTestBase() {


    @Test
    fun `metada and id are always present`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                )
            )
        ) { true }

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.id).isEqualTo(wireTransaction.id)
        Assertions.assertThat(utxoFilteredTx.metadata.getLedgerModel())
            .isEqualTo(wireTransaction.metadata.getLedgerModel())

    }

    @Test
    fun `Can reconstruct filtered output`() {
        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS_INFO.ordinal))
            .hasSize(2)
        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.OUTPUTS.ordinal))
            .hasSize(2)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.outputStateAndRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val outputs = utxoFilteredTx.outputStateAndRefs as UtxoFilteredData.Audit<StateAndRef<*>>

        Assertions.assertThat(outputs.size).isEqualTo(2)
        Assertions.assertThat(outputs.values.size).isEqualTo(2)
        Assertions.assertThat(outputs.values[0]?.state?.contractState).isInstanceOf(OutputState1::class.java)
        Assertions.assertThat(outputs.values[1]?.state?.contractState).isInstanceOf(OutputState2::class.java)
    }

    @Test
    fun `can fetch input state refs`() {
        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        Assertions.assertThat(inputStateRefs.size).isEqualTo(2)
        Assertions.assertThat(inputStateRefs.values.keys.first()).isEqualTo(0)
        Assertions.assertThat(inputStateRefs.values[0]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[0]?.index).isEqualTo(0)
        Assertions.assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }

    @Test
    fun `can filter input state refs`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is StateRef -> if (it.index == 0) false else true
                else -> true
            }
        }

        Assertions.assertThat(filteredTransaction.getComponentGroupContent(UtxoComponentGroup.INPUTS.ordinal))
            .hasSize(1)

        val utxoFilteredTx: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTx.inputStateRefs)
            .isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputStateRefs = utxoFilteredTx.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        Assertions.assertThat(inputStateRefs.size).isEqualTo(2)
        Assertions.assertThat(inputStateRefs.values.size).isEqualTo(1)
        Assertions.assertThat(inputStateRefs.values.keys.first()).isEqualTo(1)
        Assertions.assertThat(inputStateRefs.values[1]?.transactionHash).isEqualTo(inputId)
        Assertions.assertThat(inputStateRefs.values[1]?.index).isEqualTo(5)
    }


    @Test
    fun `can get notary and time window`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isEqualTo(notary)
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary but not time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.NOTARY.ordinal, Any::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is Party -> false
                else -> true
            }
        }
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isNull()
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isEqualTo(timeWindow)
    }

    @Test
    fun `can filter out notary and time window`() {
        filteredTransaction = filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS_INFO.ordinal,
                    UtxoOutputInfoComponent::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS_INFO.ordinal),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.REFERENCES.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) {
            when (it) {
                is Party -> false
                else -> true
            }
        }
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.notary).isNull()
        Assertions.assertThat(utxoFilteredTransaction.timeWindow).isNull()
    }

    @Test
    fun `can get the number of commmands but no data`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.commands)
            .isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        val commands = utxoFilteredTransaction.commands as UtxoFilteredData.SizeOnly
        Assertions.assertThat(commands.size).isEqualTo(1)
    }

    @Test
    fun `cannot find out about reference states`() {
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(serializationService, filteredTransaction)

        Assertions.assertThat(utxoFilteredTransaction.referenceInputStateRefs)
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
