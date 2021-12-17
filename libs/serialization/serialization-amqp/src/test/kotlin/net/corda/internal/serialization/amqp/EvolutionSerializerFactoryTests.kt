package net.corda.internal.serialization.amqp

import net.corda.v5.serialization.SerializedBytes
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testName
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.NotSerializableException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EvolutionSerializerFactoryTests {

    private val serializerFactoryBuilder = SerializerFactoryBuilder()

    private val nonStrictFactory = serializerFactoryBuilder.build(
            testSerializationContext.currentSandboxGroup(),
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            mustPreserveDataWhenEvolving = false
    )

    private val strictFactory = serializerFactoryBuilder.build(
            testSerializationContext.currentSandboxGroup(),
            descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry(),
            mustPreserveDataWhenEvolving = true
    )

    // Version of the class as it was serialised
    //
    // @CordaSerializable
    // data class C(val a: Int, val b: Int?)
    //
    // Version of the class as it's used in the test
    @CordaSerializable
    data class C(val a: Int)

    @Test
	fun preservesDataWhenFlagSet() {
        val resource = "${javaClass.simpleName}.${testName()}"

        val withNullResource = "${resource}_with_null"
        val withoutNullResource = "${resource}_without_null"

        // Uncomment to re-generate test files
        // val withNullOriginal = C(1, null)
        // val withoutNullOriginal = C(1, 1)
        // File(URI("$localPath/$withNullResource")).writeBytes(
        //         SerializationOutput(strictFactory).serialize(withNullOriginal).bytes)
        // File(URI("$localPath/$withoutNullResource")).writeBytes(
        //         SerializationOutput(strictFactory).serialize(withoutNullOriginal).bytes)

        val withoutNullUrl = javaClass.getResource(withoutNullResource)
        val withNullUrl = javaClass.getResource(withNullResource)

        // We can deserialize the evolved instance where the original value of 'b' is null.
        val withNullTarget = DeserializationInput(strictFactory).deserialize(SerializedBytes<C>(withNullUrl.readBytes()))
        assertEquals(1, withNullTarget.a)

        // The non-strict factory will discard the non-null original value of 'b'.
        val withNonNullTarget = DeserializationInput(nonStrictFactory).deserialize(SerializedBytes<C>(withoutNullUrl.readBytes()))
        assertEquals(1, withNonNullTarget.a)

        // The strict factory cannot deserialize the evolved instance where the original value of 'b' is non-null.
        val e = assertThrows<NotSerializableException> {
            DeserializationInput(strictFactory).deserialize(SerializedBytes<C>(withoutNullUrl.readBytes()))
        }
        assertTrue(e.message!!.contains("Non-null value 1 provided for property b, which is not supported in this version"))
    }
}