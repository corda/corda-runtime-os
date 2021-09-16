package net.corda.kryoserialization

import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializerBuilder
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

internal class KryoCheckpointSerializerBuilderImplTest {

    @Test
    fun `builder builds a serializer correctly`() {
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl()
        val sandboxGroup: SandboxGroup = mock()
        Mockito.`when`(sandboxGroup.getStaticTag(TestClass::class.java)).thenReturn("123")
        Mockito.`when`(sandboxGroup.getClass(any(), eq("123"))).thenReturn(TestClass::class.java)

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
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl()
        assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            builder.build()
        }
    }

    @Test
    fun `registering the same singleton token twice`() {
        class Tester(val someInt: Int) : SingletonSerializeAsToken
        val instance = Tester(1)
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl()
        val serializer = builder
            .newCheckpointSerializer(mockSandboxGroup(setOf(Tester::class.java)))
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .addSingletonSerializableInstances(setOf(instance))
            .addSingletonSerializableInstances(setOf(instance))
            .build()

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }
}
