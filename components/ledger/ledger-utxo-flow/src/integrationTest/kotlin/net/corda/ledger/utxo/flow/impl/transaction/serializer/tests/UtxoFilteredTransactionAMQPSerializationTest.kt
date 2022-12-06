package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoFilteredTransactionFactory
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class UtxoFilteredTransactionAMQPSerializationTest: UtxoLedgerIntegrationTest() {

    lateinit var filteredTransactionFactory: FilteredTransactionFactory
    lateinit var utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory

    @Test
    fun `can serialize and deserialize utxo filtered transaction`(){
        filteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        val filteredTx = filteredTransactionFactory.create(wireTransaction,
            listOf(
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
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.OUTPUTS.ordinal,
                    ContractState::class.java
                ),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.COMMANDS.ordinal),
            )
        ) { true }

        utxoFilteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        val utxoFilteredTx = utxoFilteredTransactionFactory.create(filteredTx)
        Assertions.assertThat(utxoFilteredTx.id).isNotNull()
    }
}