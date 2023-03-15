package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.utilities.serialization.deserialize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StateAndRefSerializerTest {
    private val serializationService = TestSerializationService.getTestSerializationService(
        { it.register(StateAndRefSerializer(), it) },
        CipherSchemeMetadataImpl()
    )

    @Test
    fun `Should serialize and then deserialize StateAndRef`() {
        val bytes = serializationService.serialize(getExampleStateAndRefImpl())
        val deserialized = serializationService.deserialize(bytes)
        assertEquals(getExampleStateAndRefImpl(), deserialized)
    }
}