package net.corda.ledger.utxo.flow.impl.transaction.serializer.tests

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.flow.transaction.filtered.factory.ComponentGroupFilterParameters
import net.corda.ledger.common.flow.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.common.testkit.createExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoFilteredTransactionFactory
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.PublicKey

class UtxoFilteredTransactionAMQPSerializationTest : UtxoLedgerIntegrationTest() {

    private companion object {
        val inputHash = SecureHash.parse("SHA256:1234567890abcdef")
    }

    @Test
    fun `can serialize and deserialize utxo filtered transaction`() {
        val filteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService<FilteredTransactionFactory>()

        val wireTx = wireTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            listOf(
                emptyList(), // Notary
                emptyList(), // Signatories
                emptyList(), // output infos
                emptyList(), // command infos
                emptyList(), // attachments
                listOf(
                    serializationService.serialize(StateRef(inputHash, 0)).bytes,
                    serializationService.serialize(StateRef(inputHash, 1)).bytes
                ), // inputs
                emptyList(), // references
                listOf(
                    serializationService.serialize(MyState(0)).bytes,
                    serializationService.serialize(MyState(1)).bytes
                ), // outputs
                emptyList(), // commands
            )
        )
        val filteredTx = filteredTransactionFactory.create(
            wireTx,
            listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java),
                ComponentGroupFilterParameters.SizeProof(UtxoComponentGroup.OUTPUTS.ordinal)
            )
        ) { true }

        val utxoFilteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService<UtxoFilteredTransactionFactory>()
        val utxoFilteredTx = utxoFilteredTransactionFactory.create(filteredTx)
        assertThat(utxoFilteredTx.id).isNotNull

        val bytes = serializationService.serialize(utxoFilteredTx)
        assertThat(bytes).isNotNull
        val deserialized = serializationService.deserialize(bytes, UtxoFilteredTransaction::class.java)

        // check that the deserialized UtxoFilteredTransaction is fully functional
        assertThat(deserialized).isNotNull
        assertThat(deserialized.id).isEqualTo(wireTx.id)

        assertThat(deserialized.commands).isInstanceOf(UtxoFilteredData.Removed::class.java)

        assertThat(deserialized.inputStateRefs).isInstanceOf(UtxoFilteredData.Audit::class.java)
        val inputs = deserialized.inputStateRefs as UtxoFilteredData.Audit<StateRef>
        assertThat(inputs.size).isEqualTo(2)
        assertThat(inputs.values.size).isEqualTo(2)
        assertThat(inputs.values[0]?.transactionHash).isEqualTo(inputHash)

        assertThat(deserialized.outputStateAndRefs).isInstanceOf(UtxoFilteredData.SizeOnly::class.java)
        val outputs = deserialized.outputStateAndRefs as UtxoFilteredData.SizeOnly
        assertThat(outputs.size).isEqualTo(2)
    }

    data class MyState(val value: Int) : ContractState {
        override val participants: List<PublicKey> = emptyList()
    }
}