package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.custom.InputStreamSerializer
import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class StreamTests {

    private class WrapperStream(input: InputStream) : FilterInputStream(input)

    @Test
	fun inputStream() {
        val attachmentInputStream = ByteArrayInputStream(ByteArray(10000))
        val id: InputStream = WrapperStream(attachmentInputStream)

        val serializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer(), this,)
        }

        val bytes = TestSerializationOutput(true, serializerFactory).serialize(id)

        val deserializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer(), this,)
        }

        DeserializationInput(serializerFactory).deserialize(bytes)
        DeserializationInput(deserializerFactory).deserialize(bytes)
    }

    @Test
	fun listInputStream() {
        val attachmentInputStream = ByteArrayInputStream(ByteArray(10000))
        val id /* : List<InputStream> */ = listOf(WrapperStream(attachmentInputStream))

        val serializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer(), this,)
        }

        val bytes = TestSerializationOutput(true, serializerFactory).serialize(id)

        val deserializerFactory = testDefaultFactory().apply {
            register(InputStreamSerializer(), this,)
        }

        DeserializationInput(serializerFactory).deserialize(bytes)
        DeserializationInput(deserializerFactory).deserialize(bytes)
    }
}