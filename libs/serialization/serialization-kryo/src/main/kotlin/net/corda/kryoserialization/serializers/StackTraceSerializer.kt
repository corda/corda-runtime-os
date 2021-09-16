package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

/**
 * We don't want to serialize the stack trace so this is a NoOp serializer
 */
class StackTraceSerializer : Serializer<Array<StackTraceElement>>() {
    override fun write(
        kryo: Kryo,
        output: Output,
        obj: Array<StackTraceElement>
    ) { }
    override fun read(
        kryo: Kryo,
        input: Input,
        type: Class<Array<StackTraceElement>>
    ): Array<StackTraceElement> = emptyArray()
}
