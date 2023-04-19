package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

internal class ProxySerializer<P : InvocationHandler?, T>(private val invocationHandlerClass: Class<P>) :
    Serializer<T>() {
    override fun write(kryo: Kryo, output: Output?, `object`: T) {
        kryo.writeObject(output, invocationHandlerClass.cast(Proxy.getInvocationHandler(`object`)))
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(kryo: Kryo, input: Input?, type: Class<out T>): T {
        val handler = kryo.readObject(input, invocationHandlerClass)
        return Proxy.newProxyInstance(
            type.classLoader, arrayOf<Class<*>>(type),
            handler
        ) as T
    }
}