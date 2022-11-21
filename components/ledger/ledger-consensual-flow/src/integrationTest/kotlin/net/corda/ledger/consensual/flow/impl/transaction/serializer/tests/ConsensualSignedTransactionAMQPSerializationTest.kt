package net.corda.ledger.consensual.flow.impl.transaction.serializer.tests

import net.corda.ledger.consensual.testkit.ConsensualLedgerIntegrationTest
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class ConsensualSignedTransactionAMQPSerializationSandboxTest: ConsensualLedgerIntegrationTest() {
    @Test
    @Suppress("FunctionName")
    fun `successfully serialize and deserialize a Consensual Signed Transaction - via sandbox serializer`() {
        val serialised = serializationService.serialize(consensualSignedTransaction)

        // Perform deserialization and check if the correct class is deserialized
        val deserialized = serializationService.deserialize(serialised)

        assertThat(deserialized.javaClass.name)
            .isEqualTo("net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl")

        assertThat(deserialized)
            .isInstanceOf(ConsensualSignedTransaction::class.java)
            .isEqualTo(consensualSignedTransaction)

        assertDoesNotThrow {
            deserialized.id
        }
        assertThat(deserialized.id).isEqualTo(consensualSignedTransaction.id)
    }
}