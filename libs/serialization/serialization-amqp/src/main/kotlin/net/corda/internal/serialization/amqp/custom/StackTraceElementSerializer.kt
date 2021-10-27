package net.corda.internal.serialization.amqp.custom

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationCustomSerializer

class StackTraceElementSerializer :
    SerializationCustomSerializer<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy> {
    override fun toProxy(obj: StackTraceElement, context: SerializationContext): StackTraceElementProxy =
        StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy, context: SerializationContext): StackTraceElement =
        StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    data class StackTraceElementProxy(
        val declaringClass: String,
        val methodName: String,
        val fileName: String?,
        val lineNumber: Int
    )
}