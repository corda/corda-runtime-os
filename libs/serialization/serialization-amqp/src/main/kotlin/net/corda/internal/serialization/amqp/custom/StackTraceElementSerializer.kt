package net.corda.internal.serialization.amqp.custom

import net.corda.internal.serialization.amqp.CustomSerializer
import net.corda.internal.serialization.amqp.LocalSerializerFactory

class StackTraceElementSerializer(factory: LocalSerializerFactory)
    : CustomSerializer.Proxy<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy>(
    StackTraceElement::class.java,
    StackTraceElementProxy::class.java,
    factory,
    withInheritance = false
) {
    override fun toProxy(obj: StackTraceElement): StackTraceElementProxy
        = StackTraceElementProxy(obj.className, obj.methodName, obj.fileName, obj.lineNumber)

    override fun fromProxy(proxy: StackTraceElementProxy): StackTraceElement
        = StackTraceElement(proxy.declaringClass, proxy.methodName, proxy.fileName, proxy.lineNumber)

    data class StackTraceElementProxy(
        val declaringClass: String,
        val methodName: String,
        val fileName: String?,
        val lineNumber: Int
    )
}
