package net.corda.schema.registry.impl

import net.corda.data.AvroEnvelope
import net.corda.data.AvroGeneratedMessageClasses.getAvroGeneratedMessageClasses
import net.corda.data.crypto.SecureHash
import net.corda.data.test.EvolvedMessage
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.ByteArrays.parseAsHex
import net.corda.v5.base.types.ByteArrays.toHexString
import org.apache.avro.Schema
import org.apache.avro.SchemaNormalization
import org.apache.avro.generic.GenericContainer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer

internal class AvroSchemaRegistryImplTest {

    private val secureHash = SecureHash("algorithm", ByteBuffer.wrap("1".toByteArray()))
    private val expectedSchemaFingerprint: ByteArray =
        SchemaNormalization.parsingFingerprint("SHA-256", secureHash.schema)
    private val avroGeneratedMessages = getAvroGeneratedMessageClasses()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `basic serde test`(compressed: Boolean) {
        val registry = AvroSchemaRegistryImpl(
            options = AvroSchemaRegistryImpl.Options(
                compressed = compressed
            )
        )
        registry.initialiseSchemas(avroGeneratedMessages)
        val encoded = registry.serialize(secureHash)

        val encodedString = toHexString(encoded.array())
        assertThat(encodedString.contains(MAGIC.toString()))
        assertThat(encodedString.contains(toHexString(expectedSchemaFingerprint)))

        val decoded = registry.deserialize<SecureHash>(encoded)
        assertThat(secureHash).isEqualTo(decoded)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `basic serde test with reusable class`(compressed: Boolean) {
        val registry = AvroSchemaRegistryImpl(
            options = AvroSchemaRegistryImpl.Options(
                compressed = compressed
            )
        )
        registry.initialiseSchemas(avroGeneratedMessages)
        val encoded = registry.serialize(secureHash)

        val encodedString = toHexString(encoded.array())
        assertThat(encodedString.contains(MAGIC.toString()))
        assertThat(encodedString.contains(toHexString(expectedSchemaFingerprint)))

        val reuse = SecureHash("reuse", ByteBuffer.wrap("3".toByteArray()))
        assertThat(secureHash).isNotEqualTo(reuse)
        registry.deserialize(encoded, reuse)
        assertThat(secureHash).isEqualTo(reuse)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `deserialize message with opposite compression`(compressed: Boolean) {
        val serializingRegistry = AvroSchemaRegistryImpl(
            options = AvroSchemaRegistryImpl.Options(
                compressed = compressed
            )
        )
        serializingRegistry.initialiseSchemas(avroGeneratedMessages)
        val encoded = serializingRegistry.serialize(secureHash)

        val encodedString = toHexString(encoded.array())
        assertThat(encodedString.contains(MAGIC.toString()))
        assertThat(encodedString.contains(toHexString(expectedSchemaFingerprint)))

        val deserializingRegistry = AvroSchemaRegistryImpl(
            options = AvroSchemaRegistryImpl.Options(
                compressed = !compressed
            )
        )
        val reuse = SecureHash("reuse", ByteBuffer.wrap("3".toByteArray()))
        assertThat(secureHash).isNotEqualTo(reuse)
        deserializingRegistry.deserialize(encoded, reuse)
        assertThat(secureHash).isEqualTo(reuse)
    }

    /**
     * Note we're only testing breaks in our headers.  Avro (or, possibly, the decompression)
     * will handle any breaks in the rest of the message for us.
     */
    @Test
    fun `broken message on transit`() {
        val registry = AvroSchemaRegistryImpl()
        registry.initialiseSchemas(avroGeneratedMessages)
        val encoded = registry.serialize(secureHash)

        // MAGIC starts at byte 0 and has value `99`
        val badMagic = encoded.duplicate()
        badMagic.array()[0] = 0
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            registry.deserialize<SecureHash>(badMagic)
        }.withMessage("Incorrect Header detected.  Cannot deserialize message.")

        // Fingerprint is at byte 8 and has value `-95`
        val badFingerprint = encoded.duplicate()
        badFingerprint.array()[8] = 0
        // Getting the class type shouldn't work
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            registry.getClassType(encoded)
        }.withMessageStartingWith("Could not find data for record with fingerprint: [0,")
        // Nor should deserializing even if we knew what to expect
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            registry.deserialize<SecureHash>(badFingerprint)
        }.withMessageStartingWith("Incorrect Header detected.  Cannot deserialize message.")
    }

    @Test
    fun `can get the class type back`() {
        val registry = AvroSchemaRegistryImpl()
        registry.initialiseSchemas(avroGeneratedMessages)
        val encoded = registry.serialize(secureHash)

        assertThat(registry.getClassType(encoded)).isEqualTo(SecureHash::class.java)
    }

    @Test
    fun `serde with non Avro message type`() {
        val registry = AvroSchemaRegistryImpl() as AvroSchemaRegistry
        val nonAvroMessage = TestMessage(1)

        registry.addSchema(
            nonAvroMessage.schema,
            TestMessage::class.java,
            nonAvroMessage::encode,
            nonAvroMessage::decode
        )

        val encoded = registry.serialize(nonAvroMessage)

        val encodedString = toHexString(encoded.array())
        assertThat(encodedString.contains(MAGIC.toString()))
        assertThat(encodedString.contains(toHexString(expectedSchemaFingerprint)))

        val decoded = registry.deserialize<TestMessage>(encoded)
        assertThat(nonAvroMessage).isEqualTo(decoded)
    }

    @Test
    fun `schema evolution`() {
        val registry = AvroSchemaRegistryImpl()
        registry.initialiseSchemas(setOf(AvroEnvelope::class.java, EvolvedMessage::class.java))
        val previousSchema = Schema.Parser()
            .parse(
                "{\"type\":\"record\"," +
                    "\"name\":\"EvolvedMessage\"," +
                    "\"namespace\":\"net.corda.data.test\"," +
                    "\"fields\":[{\"name\":\"flags\",\"type\":\"int\"}]}"
            )
        // "Here's one I made earlier"  (pre-evolved message)
        // val evolvedMessage = EvolvedMessage(5)
        // {"flags": 5}
        val encodedMessage = "636F726461010000DA291C049362E1AC80C927626090F4ECB88A0F881673325C2C994CBBA108C48400020A"
        val encoded = parseAsHex(encodedMessage)

        registry.addSchemaOnly(previousSchema)

        val evolvedMessage = EvolvedMessage(0, "")
        val decoded = registry.deserialize<EvolvedMessage>(ByteBuffer.wrap(encoded), evolvedMessage)
        assertThat(decoded.flags).isEqualTo(5)
        assertThat(decoded.extraField).isEqualTo("new_string") // The default for evolution
    }

    @Test
    fun `schema devolution`() {
        val registry = AvroSchemaRegistryImpl()
        registry.initialiseSchemas(setOf(EvolvedMessage::class.java, AvroEnvelope::class.java))
        // "Here's one I made earlier"  (pre-evolved message)
        // val evolvedMessage = EvolvedMessage(5, "evolution is cool", "no really, it is!")
        // {"flags": 5, "extra_field": "evolution is cool", "yet_another_field": "no really, it is!"}
        val evolvedMessage = "636F726461010000B1E17FA3827B5ED83904370292A687E387295C6BDAFCA4DF99AA2F2CBBAF141C004A0A2265" +
            "766F6C7574696F6E20697320636F6F6C226E6F207265616C6C792C20697420697321"

        val evolvedSchema = Schema.Parser()
            .parse(
                "{\"type\":\"record\"," +
                    "\"name\":\"EvolvedMessage\"," +
                    "\"namespace\":\"net.corda.data.test\"," +
                    "\"fields\":[{\"name\":\"flags\",\"type\":\"int\"}," +
                    "{\"name\":\"extra_field\"," +
                    "\"type\":{\"type\":\"string\"," +
                    "\"avro.java.string\":\"String\"}," +
                    "\"default\":\"new_string\"}," +
                    "{\"name\":\"yet_another_field\"," +
                    "\"type\":{\"type\":\"string\"," +
                    "\"avro.java.string\":\"String\"},\"default\":\"yes... another string!\"}]}"
            )

        registry.addSchemaOnly(evolvedSchema)

        val dummy = EvolvedMessage(0, "")

        val encoded = ByteBuffer.wrap(parseAsHex(evolvedMessage))

        val decoded = registry.deserialize(encoded, dummy)
        assertThat(decoded.flags).isEqualTo(5)
        assertThat(decoded.extraField).isEqualTo("evolution is cool")
    }

    @Disabled("Manual run only for a quick leak test.")
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `manual only - check for memory leaks test`(reuse: Boolean) {
        val registry = AvroSchemaRegistryImpl()
        registry.initialiseSchemas(avroGeneratedMessages)

        val reusable = if (reuse) SecureHash("", ByteBuffer.wrap("1".toByteArray())) else null
        // Loop the serialization/deserialization to check for leaks
        repeat(1_000_000) {
            val encoded = registry.serialize(secureHash)

            val encodedString = toHexString(encoded.array())
            assertThat(encodedString.contains(MAGIC.toString()))
            assertThat(encodedString.contains(toHexString(expectedSchemaFingerprint)))

            val decoded = registry.deserialize(encoded, reusable)
            assertThat(secureHash).isEqualTo(decoded)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `encoder exception is propagated`(compressed: Boolean) {
        val registry = AvroSchemaRegistryImpl(
            options = AvroSchemaRegistryImpl.Options(
                compressed = compressed
            )
        )
        val message = TestMessage(1)

        registry.addSchema(
            message.schema,
            TestMessage::class.java,
            { throw IllegalStateException() },
            message::decode
        )

        registry.initialiseSchemas(avroGeneratedMessages)
        assertThrows<java.lang.IllegalStateException> { registry.serialize(message) }
    }

    class TestMessage(var something: Int = 0) : GenericContainer {
        constructor() : this(0)

        companion object {
            val mySchema: Schema = Schema.Parser()
                .parse(
                    "{\"type\":\"record\"," +
                        "\"name\":\"NonAvroMessage\"," +
                        "\"namespace\":\"net.corda.data.test\"," +
                        "\"fields\":[{\"name\":\"something\",\"type\":\"int\"}]}"
                )
        }

        override fun getSchema(): Schema = mySchema

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TestMessage

            if (something != other.something) return false

            return true
        }

        override fun hashCode(): Int {
            var result = something
            result = 31 * result + (mySchema.hashCode())
            return result
        }

        @Suppress("UNUSED_PARAMETER")
        fun encode(message: TestMessage): ByteArray {
            return ByteBuffer.allocate(4).putInt(something).array()
        }

        @Suppress("UNUSED_PARAMETER")
        fun decode(encoded: ByteArray, schema: Schema, reusable: TestMessage?): TestMessage {
            val buffer = ByteBuffer.wrap(encoded)
            val message = reusable ?: TestMessage()
            message.something = buffer.int
            return message
        }
    }
}
