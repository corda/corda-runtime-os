package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@Timeout(value = 30)
class MapsSerializationTests {

    private companion object {
        val map = mapOf("foo" to "bar", "buzz" to "bull")
    }

    @Test
    fun `check EmptyMap serialization`() {
        serializeDeserializeAssert(emptyMap<Any, Any>())
    }

    @Test
    fun `check map can be root of serialization graph`() {
        serializeDeserializeAssert(map)
    }

    @CordaSerializable
    data class MyKey(val keyContent: Double)

    @CordaSerializable
    data class MyValue(val valueContent: CordaX500Name)

    @Test
    fun `check map serialization works with custom types`() {
        val myMap = mapOf(
            MyKey(1.0) to MyValue(CordaX500Name("OOO", "LLL", "CC")),
            MyKey(10.0) to MyValue(CordaX500Name("OO", "LL", "CC"))
        )
        serializeDeserializeAssert(myMap)
    }
}