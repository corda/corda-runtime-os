package net.corda.kryoserialization

import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.kryoserialization.serializers.SingletonSerializeAsTokenSerializerTest
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class KryoCheckpointSerializerBuilderImplTest {

    @Test
    fun `builder builds a serializer correctly`() {
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(mock(), mock())
        val sandboxGroup: SandboxGroup = mock()

        val serializer = builder
            .newCheckpointSerializer(sandboxGroup)
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val bytes = serializer.serialize(TestClass(TEST_INT, TEST_STRING))
        val deserialized = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(deserialized.someInt).isEqualTo(TEST_INT)
        assertThat(deserialized.someString).isEqualTo(TEST_STRING)
    }

    @Test
    fun `builder throws when newCheckpointSerializer has not been called`() {
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(mock(), mock())
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            builder.build()
        }
    }

    @Test
    fun `registering the same singleton token twice`() {
        val instance = SingletonSerializeAsTokenSerializerTest.Tester(1)
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(mock(), mock())
        val serializer = builder
            .newCheckpointSerializer(mock())
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .addSingletonSerializableInstances(setOf(instance))
            .addSingletonSerializableInstances(setOf(instance))
            .build()

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }
}
