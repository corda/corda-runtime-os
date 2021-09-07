package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.serialization.CheckpointInternalCustomSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors

class OtherTester(
    val someInt: Int,
    val someString: String
)

internal class KryoCheckpointSerializerTest {

    companion object {
        const val someInt = 1
        const val someString = "this is a string"
    }

    @Test
    fun `serialization of a simple object back a forth`() {
        val serializer = createCheckpointSerializer(
            mapOf(TestClass::class.java to TestClass.Serializer())
        )
        val tester = TestClass(someInt, someString)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
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
            val tester = TestClass(someInt, someString)
            serializedBytes = serializer.serialize(tester)
        }.get()

        // deserialize in this one
        val serializer = createCheckpointSerializer(serializers)
        val tested = serializer.deserialize(serializedBytes!!, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(someInt)
        assertThat(tested.someString).isEqualTo(someString)
    }

    @Test
    fun `serialization of a simple object using the kryo default serialization`() {
        val serializer = createCheckpointSerializer()
        val tester = TestClass(someInt, someString)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    private fun createCheckpointSerializer(
        serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> = emptyMap()
    ): KryoCheckpointSerializer {
        val checkpointContext: CheckpointSerializationContext = mock()
        whenever(checkpointContext.classInfoService).thenReturn(mock())
        whenever(checkpointContext.sandboxGroup).thenReturn(mock())

        return KryoCheckpointSerializer(
            Kryo(),
            serializers,
            mock(),
            checkpointContext
        )
    }
}
