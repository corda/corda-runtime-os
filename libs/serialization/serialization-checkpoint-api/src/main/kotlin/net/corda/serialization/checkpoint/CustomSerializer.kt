package net.corda.serialization.checkpoint

import net.corda.v5.base.types.ByteSequence

//Implemented in other modules
interface CheckpointInternalCustomSerializer<OBJ> {
    val type: Class<OBJ>

    fun write(output: CheckpointOutput, obj: OBJ)
    fun read(input: CheckpointInput, type: Class<out OBJ>): OBJ
}

//Implemented in this Module used in other modules which implement KryoSerializer
interface CheckpointInput {
    fun readClassAndObject(): Any
    fun readBytesWithLength(): ByteArray
    fun readString(): String
    fun readInt(): Int
    fun readBytes(size: Int): ByteArray
}

//Implemented in this Module used in other modules which implement KryoSerializer
interface CheckpointOutput {
    fun writeClassAndObject(obj: Any)
    fun writeBytesWithLength(encoded: ByteArray)
    fun writeString(string: String?)
    fun writeInt(int: Int)
    fun writeTo(sequence: ByteSequence)
}
