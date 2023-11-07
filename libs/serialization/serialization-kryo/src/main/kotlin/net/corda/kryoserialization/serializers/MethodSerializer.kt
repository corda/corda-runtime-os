package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.Serializer
import java.lang.reflect.Method

//TODO CORE-15338 remove this class
class MethodSerializer : Serializer<Method>() {

    override fun write(kryo: Kryo, output: Output, method: Method) {
        val declaringClass = method.declaringClass
        val methodName = method.name
        val parameterTypes = method.parameterTypes

        kryo.writeObject(output, declaringClass)
        output.writeString(methodName)
        kryo.writeObject(output, parameterTypes)
    }

    @Suppress("SpreadOperator", "TooGenericExceptionThrown")
    override fun read(kryo: Kryo, input: Input, type: Class<out Method>): Method {
        val declaringClass = kryo.readObject(input, Class::class.java)
        val methodName = input.readString()
        val parameterTypes = kryo.readObject(input, arrayOf<Class<*>>()::class.java)

        return try {
            declaringClass.getMethod(methodName, *parameterTypes)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Error reconstructing Method", e)
        }
    }
}
