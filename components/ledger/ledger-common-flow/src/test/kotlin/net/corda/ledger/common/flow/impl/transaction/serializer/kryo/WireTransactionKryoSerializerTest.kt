package net.corda.ledger.common.flow.impl.transaction.serializer.kryo

import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.test.LedgerTest
import net.corda.ledger.common.testkit.getWireTransactionExample
import net.corda.v5.ledger.common.transaction.PrivacySalt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WireTransactionKryoSerializerTest : LedgerTest() {
    @Test
    fun `serialization of a Wire Tx object using the kryo default serialization`() {
        val wireTransaction = getWireTransactionExample(
            digestService,
            merkleTreeProvider,
            jsonMarshallingService
        )
        val wireTransactionKryoSerializer = WireTransactionKryoSerializer(
            wireTransactionFactory
        )

        val serializer = createCheckpointSerializer(
            serializers = mapOf(WireTransaction::class.java to wireTransactionKryoSerializer),
            singletonInstances = emptyList(),
            extraClasses = setOf(PrivacySalt::class.java, PrivacySaltImpl::class.java)
        )

        val bytes = serializer.serialize(wireTransaction)
        val deserialized = serializer.deserialize(bytes, WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}