package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import net.corda.serialization.CheckpointInput
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointOutput
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.crypto.BasicHashingService
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

    private class Tester(
        val someInt: Int,
        val someString: String
    )

    private class TesterSerializer : CheckpointInternalCustomSerializer<Tester> {
        override fun write(output: CheckpointOutput, obj: Tester) {
            output.writeInt(obj.someInt)
            output.writeString(obj.someString)
        }

        override fun read(input: CheckpointInput, type: Class<Tester>): Tester {
            return Tester(input.readInt(), input.readString())
        }
    }

    @Test
    fun `serialization of a simple object back a forth`() {
        val serializer = createCheckpointSerializer(
            mapOf(Tester::class.java to TesterSerializer())
        )

        val tester = Tester(someInt, someString)
        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(ByteSequence.Companion.of(bytes), Tester::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    @Test
    fun `serialization of a simple object back a forth from different threads`() {
        val executor = Executors.newSingleThreadExecutor()
        val serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> =
            mapOf(Tester::class.java to TesterSerializer())

        var serializedBytes: ByteArray? = null
        // serialize in another thread
        executor.submit {
            val serializer = createCheckpointSerializer(serializers)
            val tester = Tester(someInt, someString)
            serializedBytes = serializer.serialize(tester)
        }.get()

        // deserialize in this one
        val serializer = createCheckpointSerializer(serializers)
        val tested = serializer.deserialize(ByteSequence.Companion.of(serializedBytes!!), Tester::class.java)

        assertThat(tested.someInt).isEqualTo(someInt)
        assertThat(tested.someString).isEqualTo(someString)
    }

    private fun createCheckpointSerializer(
        serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>>
    ): KryoCheckpointSerializer {
        val kryoFromQuasar = Kryo()
        val hashingService: BasicHashingService = mock()
        val checkpointContext: CheckpointSerializationContext = mock()
        whenever(checkpointContext.classInfoService).thenReturn(mock())
        whenever(checkpointContext.sandboxGroup).thenReturn(mock())

        return KryoCheckpointSerializer(
            kryoFromQuasar,
            serializers,
            emptyList(),
            hashingService,
            checkpointContext
        )
    }
}
