package net.corda.common.json.serializers

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException

class JsonSerializationUtilsTest {
    @Test
    fun `serializableClassNameFromJsonSerializer for serializer`() {
        val className = serializableClassNameFromJsonSerializer(MemberX500NameSerializer() as JsonSerializer<*>)
        assertEquals("net.corda.v5.base.types.MemberX500Name", className)
    }

    @Test
    fun `serializableClassNameFromJsonSerializer for deserializer`() {
        val className = serializableClassNameFromJsonSerializer(MemberX500NameDeserializer() as JsonDeserializer<*>)
        assertEquals("net.corda.v5.base.types.MemberX500Name", className)
    }

    @Test
    fun `serializableClassNameFromJsonSerializer for other type`() {
        assertThrows<IllegalArgumentException> {
            serializableClassNameFromJsonSerializer("string type")
        }
    }
}
