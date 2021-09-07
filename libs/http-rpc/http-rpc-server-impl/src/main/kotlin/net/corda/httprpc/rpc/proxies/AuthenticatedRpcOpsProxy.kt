@file:Suppress("DEPRECATION")
package net.corda.ext.api.rpc.proxies

import net.corda.ext.api.exception.PermissionException
import net.corda.ext.api.rpc.proxies.RpcAuthHelper.methodFullName
import net.corda.ext.internal.rpc.security.RpcAuthContext
import net.corda.ext.internal.rpc.security.rpcContext
import net.corda.v5.application.messaging.RPCOps
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Creates proxy that checks entitlements for every RPCOps interface call.
 */
internal object AuthenticatedRpcOpsProxy {

    fun <T : RPCOps> proxy(delegate: T, targetInterface: Class<out T>): T {
        require(targetInterface.isInterface) { "Interface is expected instead of $targetInterface" }
        val handler = PermissionsEnforcingInvocationHandler(delegate, targetInterface)
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(delegate::class.java.classLoader, arrayOf(targetInterface), handler) as T
    }

    private class PermissionsEnforcingInvocationHandler(override val delegate: Any, private val clazz: Class<*>) : InvocationHandlerTemplate {

        private val exemptMethod = RPCOps::class.java.getMethod("getProtocolVersion")

        override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {

            if (method == exemptMethod) {
                // "getProtocolVersion" is an exempt from entitlements check as this is the very first *any* RPCClient calls upon login
                return super.invoke(proxy, method, arguments)
            }

            return guard(method, ::rpcContext) { super.invoke(proxy, method, arguments) }
        }

        private fun <RESULT> guard(method: Method, context: () -> RpcAuthContext, action: () -> RESULT): RESULT {
            if (!context().isPermitted(methodFullName(method))) {
                throw PermissionException("User not authorized to perform RPC call $method.")
            } else {
                return action()
            }
        }
    }
}

object RpcAuthHelper {
    const val INTERFACE_SEPARATOR = "#"

    fun methodFullName(method: Method): String = methodFullName(method.declaringClass, method.name)

    fun standardOpsFullNameMapping(method: String): String {
        return methodFullName(RPCOps::class.java, method)
    }

    fun methodFullName(clazz: Class<*>, methodName: String): String {
        require(clazz.isInterface) { "Must be an interface: $clazz"}
        require(RPCOps::class.java.isAssignableFrom(clazz)) { "Must be assignable from RPCOps: $clazz" }
        return clazz.name + INTERFACE_SEPARATOR + methodName
    }
}
