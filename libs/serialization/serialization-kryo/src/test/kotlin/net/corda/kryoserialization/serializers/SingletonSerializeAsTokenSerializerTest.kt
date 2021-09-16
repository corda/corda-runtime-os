package net.corda.kryoserialization.serializers

import net.corda.kryoserialization.createCheckpointSerializer
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class SingletonSerializeAsTokenSerializerTest {

    class Tester(val someInt: Int) : SingletonSerializeAsToken

    @Test
    fun `singleton serializer returns the correct instance back`() {
        val instance = Tester(1)
        val serializer = createCheckpointSerializer(singletonInstances = listOf(instance))

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }

    @Test
    fun `singleton serializer throws on serialize when instance not registered`() {
        val instance = Tester(1)
        val serializer = createCheckpointSerializer()

        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            serializer.serialize(instance)
        }
    }

    @Test
    fun `singleton serializer throws on deserialize when instance not registered`() {
        val instance = Tester(1)
        val serializer = createCheckpointSerializer(singletonInstances = listOf(instance))
        val deserializer = createCheckpointSerializer()

        val bytes = serializer.serialize(instance)
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            deserializer.deserialize(bytes, instance::class.java)
        }
    }

}
