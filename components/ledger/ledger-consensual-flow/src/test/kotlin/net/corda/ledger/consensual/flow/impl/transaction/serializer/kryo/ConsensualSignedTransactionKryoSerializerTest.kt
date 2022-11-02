package net.corda.ledger.consensual.flow.impl.transaction.serializer.kryo

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.common.flow.impl.transaction.serializer.kryo.WireTransactionKryoSerializer
import net.corda.ledger.common.test.LedgerTest
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.testkit.getConsensualSignedTransactionExample
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.DigitalSignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ConsensualSignedTransactionKryoSerializerTest: LedgerTest() {
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(WireTransactionSerializer(wireTransactionFactory), it)
    }, cipherSchemeMetadata)

    @Test
    fun `serialization of a Consensual Signed Tx object using the kryo default serialization`() {
        val wireTransactionKryoSerializer = WireTransactionKryoSerializer(wireTransactionFactory)
        val consensualSignedTransactionKryoSerializer = ConsensualSignedTransactionKryoSerializer(
            serializationService,
            mockSigningService(),
            mock()
        )

        val signedTransaction = getConsensualSignedTransactionExample(
            digestService,
            merkleTreeProvider,
            serializationService,
            jsonMarshallingService,
            mockSigningService(),
            mock()
        )

        val serializer = createCheckpointSerializer(
            mapOf(
                WireTransaction::class.java to wireTransactionKryoSerializer,
                ConsensualSignedTransactionImpl::class.java to consensualSignedTransactionKryoSerializer
            ),
            emptyList(),
            setOf(
                PrivacySaltImpl::class.java,
                DigitalSignatureAndMetadata::class.java,
                signedTransaction.signatures[0].by::class.java,
                emptyMap<String, String>()::class.java,
                DigitalSignature.WithKey::class.java
            )
        )
        val bytes = serializer.serialize(signedTransaction)
        val deserialized = serializer.deserialize(bytes, ConsensualSignedTransactionImpl::class.java)

        assertThat(deserialized).isEqualTo(signedTransaction)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(signedTransaction.id, deserialized.id)
    }
}