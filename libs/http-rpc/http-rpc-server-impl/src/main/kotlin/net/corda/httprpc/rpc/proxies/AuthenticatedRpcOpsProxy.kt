@file:Suppress("DEPRECATION")
package net.corda.httprpc.rpc.proxies

import net.corda.v5.application.messaging.RPCOps
import java.lang.reflect.Method


object RpcAuthHelper {
    const val INTERFACE_SEPARATOR = "#"

    fun methodFullName(method: Method): String = methodFullName(method.declaringClass, method.name)

    fun methodFullName(clazz: Class<*>, methodName: String): String {
        require(clazz.isInterface) { "Must be an interface: $clazz"}
        require(RPCOps::class.java.isAssignableFrom(clazz)) { "Must be assignable from RPCOps: $clazz" }
        return clazz.name + INTERFACE_SEPARATOR + methodName
    }
}
