package net.corda.ledger.consensual.flow.impl.transaction.serializer.tests

import net.corda.ledger.consensual.testkit.ConsensualLedgerIntegrationTest
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConsensualSignedTransactionKryoSerializationTest : ConsensualLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `correct serialization of a consensual Signed Transaction`() {
        val bytes = kryoSerializer.serialize(consensualSignedTransaction)
        val deserialized = kryoSerializer.deserialize(bytes, ConsensualSignedTransaction::class.java)

        assertThat(deserialized).isEqualTo(consensualSignedTransaction)
        Assertions.assertDoesNotThrow { deserialized.id }
        Assertions.assertEquals(consensualSignedTransaction.id, deserialized.id)
    }
}
