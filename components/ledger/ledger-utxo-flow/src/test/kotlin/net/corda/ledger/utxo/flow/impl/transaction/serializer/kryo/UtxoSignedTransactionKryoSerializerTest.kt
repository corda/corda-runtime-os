package net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo

import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.DigitalSignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtxoSignedTransactionKryoSerializerTest: UtxoLedgerTest() {
    @Test
    fun `serialization of a Utxo Signed Tx object using the kryo default serialization`() {
        val serializer = createCheckpointSerializer(
            mapOf(
                WireTransaction::class.java to wireTransactionKryoSerializer,
                UtxoSignedTransactionImpl::class.java to utxoSignedTransactionKryoSerializer
            ),
            emptyList(),
            setOf(
                PrivacySaltImpl::class.java,
                DigitalSignatureAndMetadata::class.java,
                utxoSignedTransactionExample.signatures[0].by::class.java,
                emptyMap<String, String>()::class.java,
                emptyList<String>()::class.java,
                DigitalSignature.WithKey::class.java,
                mapOf("" to "")::class.java
            )
        )
        val bytes = serializer.serialize(utxoSignedTransactionExample)
        val deserialized = serializer.deserialize(bytes, UtxoSignedTransactionImpl::class.java)

        assertThat(deserialized).isEqualTo(utxoSignedTransactionExample)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(utxoSignedTransactionExample.id, deserialized.id)
    }
}