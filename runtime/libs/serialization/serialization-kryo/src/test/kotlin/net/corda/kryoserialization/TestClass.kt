package net.corda.kryoserialization

import net.corda.serialization.checkpoint.CheckpointInput
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import net.corda.serialization.checkpoint.CheckpointOutput
import net.corda.serialization.checkpoint.NonSerializable

internal class TestClass(
    val someInt: Int,
    val someString: String
) {
    companion object {
        const val TEST_INT = 1
        const val TEST_STRING = "test"
    }

    internal class Serializer : CheckpointInternalCustomSerializer<TestClass> {
        override val type: Class<TestClass> get() = TestClass::class.java
        override fun write(output: CheckpointOutput, obj: TestClass) {
            output.writeInt(obj.someInt)
            output.writeString(obj.someString)
        }

        override fun read(input: CheckpointInput, type: Class<out TestClass>): TestClass {
            return TestClass(input.readInt(), input.readString())
        }
    }
}

internal class NonSerializableTestClass : NonSerializable {
    internal class Serializer : CheckpointInternalCustomSerializer<NonSerializableTestClass> {
        override val type: Class<NonSerializableTestClass> get() = NonSerializableTestClass::class.java
        override fun write(output: CheckpointOutput, obj: NonSerializableTestClass) {
        }

        override fun read(input: CheckpointInput, type: Class<out NonSerializableTestClass>): NonSerializableTestClass {
            return NonSerializableTestClass()
        }
    }
}
