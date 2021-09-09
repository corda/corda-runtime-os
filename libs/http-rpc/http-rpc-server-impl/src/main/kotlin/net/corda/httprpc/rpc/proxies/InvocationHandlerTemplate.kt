package net.corda.httprpc.rpc.proxies

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Helps writing correct [InvocationHandler]s.
 */
@Suppress("SpreadOperator")
interface InvocationHandlerTemplate : InvocationHandler {
    val delegate: Any

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        val args = arguments ?: emptyArray()
        return try {
            method.invoke(delegate, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}