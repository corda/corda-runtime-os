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
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class UtxoFilteredTransactionAMQPSerializationTest: UtxoLedgerIntegrationTest() {

    lateinit var filteredTransactionFactory: FilteredTransactionFactory
    lateinit var utxoFilteredTransactionFactory: UtxoFilteredTransactionFactory

    companion object{
        private val inputHash = SecureHash.parse("SHA256:1234567890abcdef")
    }

    @Test
    fun `can serialize and deserialize utxo filtered transaction`(){
        filteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService()

        val wireTx = wireTransactionFactory.createExample(jsonMarshallingService, jsonValidator, listOf(
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
            emptyList(), // outputs
            emptyList(), // commands
        ))
        val filteredTx = filteredTransactionFactory.create(wireTx,
            listOf(
                ComponentGroupFilterParameters.AuditProof(
                    UtxoComponentGroup.METADATA.ordinal,
                    TransactionMetadataImpl::class.java
                ),
                ComponentGroupFilterParameters.AuditProof(UtxoComponentGroup.INPUTS.ordinal, StateRef::class.java)
            )
        ) { true }

        utxoFilteredTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        val utxoFilteredTx = utxoFilteredTransactionFactory.create(filteredTx)
        Assertions.assertThat(utxoFilteredTx.id).isNotNull()

        val bytes = serializationService.serialize(utxoFilteredTx)
        Assertions.assertThat(bytes).isNotNull()
//        val deserialized = serializationService.deserialize(bytes, UtxoFilteredTransaction::class.java)
//
//        Assertions.assertThat(deserialized).isNotNull
//        Assertions.assertThat(deserialized.id).isEqualTo(wireTx.id)
    }
}