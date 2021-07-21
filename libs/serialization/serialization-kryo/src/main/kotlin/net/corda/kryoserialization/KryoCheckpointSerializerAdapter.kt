package net.corda.kryoserialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.v5.base.types.ByteSequence
import net.corda.internal.serialization.checkpoints.CheckpointInput
import net.corda.internal.serialization.checkpoints.CheckpointInternalCustomSerializer
import net.corda.internal.serialization.checkpoints.CheckpointOutput

class KryoCheckpointSerializerAdapter<OBJ>(val checkpointSerializer : CheckpointInternalCustomSerializer<OBJ>) {

    private inner class KryoCheckpointOutput(val kryo: Kryo, val output: Output): CheckpointOutput {
        override fun writeClassAndObject(obj: Any) {
            kryo.writeClassAndObject(output, obj)
        }

        override fun writeBytesWithLength(encoded: ByteArray) {
            output.writeBytesWithLength(encoded)
        }

        override fun writeString(string: String?) {
            output.writeString(string)
        }

        override fun writeInt(int: Int) {
            output.writeInt(int)
        }

        override fun isSerializeAsToken(): Boolean {
            return (kryo.serializationContext() != null)
        }

        override fun writeTo(sequence: ByteSequence) {
            sequence.writeTo(output)
        }
    }

    private inner class KryoCheckpointInput(val kryo: Kryo, val input: Input): CheckpointInput {
        override fun readClassAndObject(): Any {
            return kryo.readClassAndObject(input)
        }

        override fun readBytesWithLength(): ByteArray {
            return input.readBytesWithLength()
        }

        override fun isSerializeAsToken(): Boolean {
            return (kryo.serializationContext() != null)
        }

        override fun getSingleton(name: String): Any? {
            return kryo.serializationContext()?.fromIdentifier(name)
        }

        override fun readString(): String {
            return input.readString()
        }

        override fun readInt(): Int {
            return input.readInt()
        }

        override fun readBytes(size: Int): ByteArray {
            return input.readBytes(size)
        }
    }

    private inner class KyroCheckpointSerializer : Serializer<OBJ>() {
        override fun write(kryo: Kryo?, output: Output?, obj: OBJ) {
            val adaptedOutput = KryoCheckpointOutput(kryo!!, output!!)
            checkpointSerializer.write(adaptedOutput, obj)
        }

        override fun read(kryo: Kryo?, input: Input?, type: Class<OBJ>?): OBJ {
            val adaptedInput = KryoCheckpointInput(kryo!!, input!!)
            return checkpointSerializer.read(adaptedInput, type!!)
        }
    }

    fun adapt(): Serializer<OBJ> {
        return KyroCheckpointSerializer()
    }
}