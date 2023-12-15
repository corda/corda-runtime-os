package net.corda.ledger.common.flow.impl.transaction.serializer.kryo.tests

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.integration.test.CommonLedgerIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WireTransactionKryoSerializationTest : CommonLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a wire Transaction`() {
        val bytes = kryoSerializer.serialize(wireTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, WireTransaction::class.java)

        assertThat(deserialized).isEqualTo(wireTransaction)
        assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}
