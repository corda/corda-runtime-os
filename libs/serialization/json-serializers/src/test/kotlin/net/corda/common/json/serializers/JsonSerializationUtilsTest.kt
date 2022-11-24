package net.corda.common.json.serializers

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonSerializationUtilsTest {
    @Test
    fun `serializableClassNameFromJsonSerializer for serializer`() {
        val clazz = serializableClassFromJsonSerializer(MemberX500NameSerializer() as JsonSerializer<*>)
        assertEquals(MemberX500Name::class.java, clazz)
    }

    @Test
    fun `serializableClassNameFromJsonSerializer for deserializer`() {
        val clazz = serializableClassFromJsonSerializer(MemberX500NameDeserializer() as JsonDeserializer<*>)
        assertEquals(MemberX500Name::class.java, clazz)
    }

    @Test
    fun `serializableClassNameFromJsonSerializer for other type`() {
        assertThrows<IllegalArgumentException> {
            serializableClassFromJsonSerializer("string type")
        }
    }
}
