package net.corda.ledger.consensual.data.transaction.serializer.amqp

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.serializer.amqp.WireTransactionSerializer
import net.corda.ledger.consensual.testkit.getConsensualSignedTransaction
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.deserialize
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class ConsensualSignedTransactionSerializerTest {
    private val schemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = DigestServiceImpl(schemeMetadata, null)
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val serializationServiceNullCfg = TestSerializationService.getTestSerializationService({}, schemeMetadata)
    private val signingService: SigningService = mock()
    private val serializationService = TestSerializationService.getTestSerializationService({
        it.register(WireTransactionSerializer(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService
        ), it)
        it.register(ConsensualSignedTransactionSerializer(serializationServiceNullCfg, signingService, mock()), it)
    }, schemeMetadata)

    @Test
    fun `Should serialize and then deserialize wire Tx`() {

        val signedTransaction = getConsensualSignedTransaction(
            digestService,
            merkleTreeProvider,
            serializationService,
            jsonMarshallingService,
            signingService,
            mock()
        )

        val bytes = serializationService.serialize(signedTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(signedTransaction, deserialized)
        Assertions.assertDoesNotThrow {
            deserialized.id
        }
        assertEquals(signedTransaction.id, deserialized.id)
    }
}
