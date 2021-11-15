package net.corda.internal.serialization.amqp.custom

import net.corda.serialization.BaseProxySerializer

class StackTraceElementSerializer : BaseProxySerializer<StackTraceElement, StackTraceElementSerializer.StackTraceElementProxy>() {
    override val type: Class<StackTraceElement> get() = StackTraceElement::class.java
    override val proxyType: Class<StackTraceElementProxy> get() = StackTraceElementProxy::class.java
    override val withInheritance: Boolean get() = false

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
