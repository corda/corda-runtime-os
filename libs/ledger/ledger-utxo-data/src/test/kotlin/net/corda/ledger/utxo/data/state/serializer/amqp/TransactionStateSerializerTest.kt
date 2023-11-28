package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.data.state.EncumbranceGroupImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.utilities.serialization.deserialize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TransactionStateSerializerTest {
    private val serializationService = TestSerializationService.getTestSerializationService(
        { it.register(TransactionStateSerializer(), it) },
        CipherSchemeMetadataImpl()
    )

    @Test
    fun `Should serialize and then deserialize transaction state`() {
        val transactionState = TransactionStateImpl(
            getUtxoStateExample(),
            notaryX500Name,
            publicKeyExample,
            null
        )
        val bytes = serializationService.serialize(transactionState)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(transactionState, deserialized)
    }

    @Test
    fun `Should serialize and then deserialize transaction state with encumbrance`() {
        val transactionState = TransactionStateImpl(
            getUtxoStateExample(),
            notaryX500Name,
            publicKeyExample,
            EncumbranceGroupImpl(5, "tag")
        )
        val bytes = serializationService.serialize(transactionState)
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(transactionState, deserialized)
    }
}
