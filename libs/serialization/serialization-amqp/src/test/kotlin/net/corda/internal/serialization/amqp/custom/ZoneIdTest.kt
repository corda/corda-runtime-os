package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.serializeDeserializeAssert
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert.Companion.factory
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId

class ZoneIdTest {

    companion object {
        @JvmStatic
        fun everyZoneId(): List<ZoneId> {
            return ZoneId.getAvailableZoneIds().map { ZoneId.of(it) }
        }
    }

    @ParameterizedTest
    @MethodSource("everyZoneId")
    fun everyZoneId(zoneId: ZoneId) {
        serializeDeserializeAssert(zoneId)
    }

    @Test
    fun testSerializerIsRegisteredForSubclass() {
        val zone = ZoneId.of("Asia/Aden")
        assertNotSame(ZoneId::class.java, zone::class.java)

        val schemas = SerializationOutput(factory).serializeAndReturnSchema(zone, testSerializationContext)
        assertThat(schemas.schema.types.map(TypeNotation::name)).contains(zone::class.java.name)

        val serializer = factory.findCustomSerializer(zone::class.java, zone::class.java)
            ?: fail("No custom serializer found")
        assertThat(serializer.type).isSameAs(zone::class.java)
    }
}
