package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * Corda 4.4 briefly serialised [Enum] values using [Enum.toString] rather
 * than [Enum.name]. We need to be able to deserialise these values now
 * that the bug has been fixed.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EnumToStringFallbackTest {
    private lateinit var serializationOutput: TestSerializationOutput

    @BeforeEach
    fun setup() {
        serializationOutput = TestSerializationOutput(verbose = false)
    }

    @Test
    fun deserializeEnumWithToString() {
        val broken = BrokenContainer(Broken.Twice)
        val brokenData = serializationOutput.serialize(broken, testSerializationContext)
        val workingData = brokenData.rewriteAsWorking()
        val working = DeserializationInput(testDefaultFactory()).deserialize(workingData, testSerializationContext)
        assertThat(working.value).isEqualTo(Working.TWO)
    }

    /**
     * This function rewrites the [SerializedBytes] for a [Broken]
     * property that has been composed inside a [BrokenContainer].
     * It will generate the [SerializedBytes] that Corda 4.4 would
     * generate for an equivalent [WorkingContainer].
     */
    @Suppress("unchecked_cast")
    private fun SerializedBytes<BrokenContainer>.rewriteAsWorking(): SerializedBytes<WorkingContainer> {
        val envelope = DeserializationInput.getEnvelope(this).apply {
            val compositeType = schema.types[0] as CompositeType
            (schema.types as MutableList<TypeNotation>)[0] = compositeType.copy(
                name = toWorking(compositeType.name),
                fields = compositeType.fields.map { it.copy(type = toWorking(it.type)) }
            )
            val restrictedType = schema.types[1] as RestrictedType
            (schema.types as MutableList<TypeNotation>)[1] = restrictedType.copy(
                name = toWorking(restrictedType.name)
            )
        }
        return SerializedBytes(envelope.write())
    }

    private fun toWorking(oldName: String): String = oldName.replace("Broken", "Working")

    /**
     * This is the enumerated type, as it actually exist.
     */
    @Suppress("unused")
    @CordaSerializable
    enum class Working(private val label: String) {
        ZERO("None"),
        ONE("Once"),
        TWO("Twice");

        @Override
        override fun toString(): String = label
    }

    /**
     * This represents a broken serializer's view of the [Working]
     * enumerated type, which would serialize using [Enum.toString]
     * rather than [Enum.name].
     */
    @Suppress("unused")
    @CordaSerializable
    enum class Broken(private val label: String) {
        None("None"),
        Once("Once"),
        Twice("Twice");

        @Override
        override fun toString(): String = label
    }

    @CordaSerializable
    data class WorkingContainer(val value: Working)
    @CordaSerializable
    data class BrokenContainer(val value: Broken)
}
