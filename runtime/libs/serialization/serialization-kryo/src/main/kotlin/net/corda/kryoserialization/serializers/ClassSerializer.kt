package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.sandbox.SandboxGroup

class ClassSerializer(
    private val sandboxGroup: SandboxGroup
) : Serializer<Class<*>>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out Class<*>>): Class<*> {
        return sandboxGroup.getClass(input.readString(), input.readString())
    }

    override fun write(kryo: Kryo, output: Output, clazz: Class<*>) {
        output.writeString(clazz.name)
        output.writeString(sandboxGroup.getStaticTag(clazz))
    }
}

