package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.Serializer
import java.lang.reflect.Method

//TODO CORE-15338 remove this class
class MethodSerializer : Serializer<Method>() {

    override fun write(kryo: Kryo, output: Output, method: Method) {
        val declaringClassName = method.declaringClass.name
        val methodName = method.name
        val parameterTypes = method.parameterTypes

        output.writeString(declaringClassName)
        output.writeString(methodName)
        kryo.writeObject(output, parameterTypes)
    }

    @Suppress("SpreadOperator", "TooGenericExceptionThrown")
    override fun read(kryo: Kryo, input: Input, type: Class<out Method>): Method {
        val declaringClassName = input.readString()
        val methodName = input.readString()
        val parameterTypes = kryo.readObject(input, arrayOf<Class<*>>()::class.java)

        return try {
            val declaringClass = Class.forName(declaringClassName)
            declaringClass.getMethod(methodName, *parameterTypes)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Error reconstructing Method", e)
        }
    }
}
