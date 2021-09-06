package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.custom.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import org.junit.jupiter.api.Test
import java.util.*

class OptionalTest {
    @Test
    fun empty() {
        serializeDeserializeAssert(Optional.empty<String>())
    }
    @Test
    fun nullableString() {
        serializeDeserializeAssert(Optional.ofNullable("TEST"))
    }
    @Test
    fun string() {
        serializeDeserializeAssert(Optional.of("TEST"))
    }
    @Test
    fun nullableNull() {
        serializeDeserializeAssert(Optional.ofNullable(null))
    }
}