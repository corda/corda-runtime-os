package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.kryoserialization.testkit.mockSandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Executors

internal class KryoCheckpointSerializerTest {

    @Test
    fun `serialization of a simple object back a forth`() {
        val serializer = createCheckpointSerializer(
            mapOf(TestClass::class.java to TestClass.Serializer())
        )
        val tester = TestClass(TEST_INT, TEST_STRING)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    @Test
    fun `serialization of a non-serializable type throws an exception`() {
        val serializer = createCheckpointSerializer(
            mapOf(NonSerializableTestClass::class.java to NonSerializableTestClass.Serializer())
        )

        val tester = NonSerializableTestClass()

        val error = assertThrows<UnsupportedOperationException> { serializer.serialize(tester) }
        assertThat(error.message).isEqualTo(
            "net.corda.kryoserialization.NonSerializableTestClass, " +
                    "has been marked as a non-serializable type is should never be serialised into a checkpoint."
        )
    }

    @Test
    fun `serialization of an avro generated class throws an exception`() {
        val sandboxGroup = mockSandboxGroup(setOf(FlowStackItem::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(MapReferenceResolver()),
                emptyMap(),
                CordaClassResolver(sandboxGroup),
                ClassSerializer(sandboxGroup)
            )
        )

        assertThatThrownBy { serializer.serialize(FlowStackItem()) }
            .isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessage("${FlowStackItem::class.java.canonicalName} is an avro generated class and should never be " +
                    "serialised into a checkpoint.")
    }

    @Test
    fun `serialization of a simple object back a forth from different threads`() {
        val executor = Executors.newSingleThreadExecutor()
        val serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> =
            mapOf(TestClass::class.java to TestClass.Serializer())

        var serializedBytes: ByteArray? = null
        // serialize in another thread
        executor.submit {
            val serializer = createCheckpointSerializer(serializers)
            val tester = TestClass(TEST_INT, TEST_STRING)
            serializedBytes = serializer.serialize(tester)
        }.get()

        // deserialize in this one
        val serializer = createCheckpointSerializer(serializers)
        val tested = serializer.deserialize(serializedBytes!!, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(TEST_INT)
        assertThat(tested.someString).isEqualTo(TEST_STRING)
    }

    @Test
    fun `serialization of a simple object using the kryo default serialization`() {
        val sandboxGroup = mockSandboxGroup(setOf(TestClass::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(MapReferenceResolver()),
                emptyMap(),
                CordaClassResolver(sandboxGroup),
                ClassSerializer(sandboxGroup)
            )
        )
        val tester = TestClass(TEST_INT, TEST_STRING)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }
}
