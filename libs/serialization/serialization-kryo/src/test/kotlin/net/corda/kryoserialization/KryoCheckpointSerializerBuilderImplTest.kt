package net.corda.kryoserialization

import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class KryoCheckpointSerializerBuilderImplTest {

    @Test
    fun `builder builds a serializer correctly`() {
        val sandboxGroup: SandboxGroup = mockSandboxGroup(setOf(TestClass::class.java))
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(mock(), sandboxGroup)

        val serializer = builder
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val bytes = serializer.serialize(TestClass(TEST_INT, TEST_STRING))
        val deserialized = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(deserialized.someInt).isEqualTo(TEST_INT)
        assertThat(deserialized.someString).isEqualTo(TEST_STRING)
    }

    @Test
    fun `registering the same singleton token twice`() {
        class Tester(val someInt: Int) : SingletonSerializeAsToken
        val instance = Tester(1)
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(
            mock(),
            mockSandboxGroup(setOf(Tester::class.java))
        )
        val serializer = builder
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .addSingletonSerializableInstances(setOf(instance))
            .addSingletonSerializableInstances(setOf(instance))
            .build()

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }
}
