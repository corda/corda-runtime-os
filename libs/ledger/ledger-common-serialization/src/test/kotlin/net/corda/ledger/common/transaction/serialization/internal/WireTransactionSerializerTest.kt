package net.corda.ledger.common.transaction.serialization.internal

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.impl.transaction.WireTransactionDigestSettings
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions

class WireTransactionSerializerTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory
        private lateinit var serializationService: SerializationService

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
            serializationService = TestSerializationService.getTestSerializationService({
                it.register(WireTransactionSerializer(merkleTreeFactory, digestService), it)
            }, schemeMetadata)
        }
    }

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val mapper = jacksonObjectMapper()
        val transactionMetaData = TransactionMetaData(
            mapOf(
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
            )
        )
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf(mapper.writeValueAsBytes(transactionMetaData)),
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists
        )
        val bytes = serializationService.serialize(wireTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(wireTransaction, deserialized)
        Assertions.assertDoesNotThrow{
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}
