package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testResourceName
import net.corda.internal.serialization.amqp.testutils.writeTestResource
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ThrowableEvolutionTests {
    private val toBeRemovedValue = "Remove"
    private val message = "Test Message"

    /**
     * AddConstructorParametersException with an extra parameter called "added"
     */
    @CordaSerializable
//    class AddConstructorParametersException(message: String) : CordaRuntimeException(message)
    class AddConstructorParametersException(message: String, val added: String?) : CordaRuntimeException(message)

    /**
     * RemoveConstructorParametersException with the "toBeRemoved" parameter removed
     */
    @CordaSerializable
//    class RemoveConstructorParametersException(message: String, val toBeRemoved: String) : CordaRuntimeException(message)
    class RemoveConstructorParametersException(message: String) : CordaRuntimeException(message)

    /**
     * AddAndRemoveConstructorParametersException with the "toBeRemoved" parameter removed and "added" added
     */
    @CordaSerializable
//    class AddAndRemoveConstructorParametersException(message: String, val toBeRemoved: String) : CordaRuntimeException(message)
    class AddAndRemoveConstructorParametersException(message: String, val added: String?) : CordaRuntimeException(message)

    @Test
    fun addConstructorParametersException() {

//        val exception = AddConstructorParametersException(message)
//        saveSerializedObject(exception)

        val bytes = requireNotNull(this::class.java.getResource(testResourceName())).readBytes()

        val sf = testDefaultFactory().also { registerCustomSerializers(it) }
        val deserializedException = DeserializationInput(sf)
            .deserialize(SerializedBytes<AddConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(AddConstructorParametersException::class.java)
    }

    @Test
    fun removeConstructorParametersException() {

//        val exception = RemoveConstructorParametersException(message, toBeRemovedValue)
//        saveSerializedObject(exception)

        val bytes = requireNotNull(this::class.java.getResource(testResourceName())).readBytes()

        val sf = testDefaultFactory().also { registerCustomSerializers(it) }
        val deserializedException = DeserializationInput(sf)
            .deserialize(SerializedBytes<RemoveConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(RemoveConstructorParametersException::class.java)
    }

    @Test
    fun addAndRemoveConstructorParametersException() {

//        val exception = AddAndRemoveConstructorParametersException(message, toBeRemovedValue)
//        saveSerializedObject(exception)

        val bytes = requireNotNull(this::class.java.getResource(testResourceName())).readBytes()

        val sf = testDefaultFactory().also { registerCustomSerializers(it) }
        val deserializedException = DeserializationInput(sf)
            .deserialize(SerializedBytes<AddAndRemoveConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(AddAndRemoveConstructorParametersException::class.java)
    }

    /**
     * Write serialized object to resources folder
     */
    @Suppress("unused")
    fun <T : Any> saveSerializedObject(obj : T){

        val sf = testDefaultFactory().also { registerCustomSerializers(it) }
        val serializedBytes = SerializationOutput(sf).serialize(obj)
        writeTestResource(serializedBytes)
    }
}