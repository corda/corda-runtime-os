package net.corda.ledger.common.impl.transaction.serialization

import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.osgi.test.common.annotation.InjectService

@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class WireTransactionSerializerTest {

    @InjectService(timeout = 1000)
    lateinit var serializationService: SerializationService

    @InjectService(timeout = 1000)
    lateinit var merkleTreeFactory: MerkleTreeFactory

    @InjectService(timeout = 1000)
    lateinit var digestService: DigestService

    @Test
    fun `Should serialize and then deserialize wire Tx`() {
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf("123".toByteArray(), "45678".toByteArray()),
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(merkleTreeFactory, digestService, privacySalt, componentGroupLists)
        val bytes = serializationService.serialize(wireTransaction)
        val deserialized = serializationService.deserialize(bytes)
        assertThat(wireTransaction == deserialized)
    }
}
