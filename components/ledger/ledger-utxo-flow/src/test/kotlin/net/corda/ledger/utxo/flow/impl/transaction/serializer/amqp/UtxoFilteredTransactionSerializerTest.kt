package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.flow.impl.transaction.filtered.serializer.amqp.FilteredTransactionSerializer
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionTestBase
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UtxoFilteredTransactionSerializerTest: UtxoLedgerTest() {
    private val testBase = UtxoFilteredTransactionTestBase()
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(wireTransactionAMQPSerializer, it)
        it.register(FilteredTransactionSerializer(jsonMarshallingService, merkleTreeProvider), it)
        it.register(UtxoFilteredTransactionSerializer(serializationServiceNullCfg), it)
    }, cipherSchemeMetadata)

    @BeforeEach
    fun beforeEach()
    {
        testBase.beforeEach()
    }

    @Test
    fun `should serialize and then deserialize a utxo filtered transaction`(){
        val utxoFilteredTransaction: UtxoFilteredTransaction =
            UtxoFilteredTransactionImpl(testBase.serializationService, testBase.filteredTransaction)
        val bytes = serializationService.serialize(utxoFilteredTransaction)
        val deserialized = serializationService.deserialize(bytes)

        Assertions.assertThat(deserialized.id).isEqualTo(utxoFilteredTransaction.id)
    }

}