package net.corda.kryoserialization

import net.corda.serialization.CheckpointInput
import net.corda.serialization.CheckpointInternalCustomSerializer
import net.corda.serialization.CheckpointOutput

internal class TestClass(
    val someInt: Int,
    val someString: String
) {
    companion object {
        const val TEST_INT = 1
        const val TEST_STRING = "test"
    }

    internal class Serializer : CheckpointInternalCustomSerializer<TestClass> {
        override fun write(output: CheckpointOutput, obj: TestClass) {
            output.writeInt(obj.someInt)
            output.writeString(obj.someString)
        }

        override fun read(input: CheckpointInput, type: Class<TestClass>): TestClass {
            return TestClass(input.readInt(), input.readString())
        }
    }
}
