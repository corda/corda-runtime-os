package net.corda.httprpc.security

import org.slf4j.MDC

data class RpcAuthContext(
    val invocation: InvocationContext,
    private val authorizer: AuthorizingSubject
) : AuthorizingSubject by authorizer


@JvmField
val CURRENT_RPC_CONTEXT: ThreadLocal<RpcAuthContext> = CurrentRpcContext()

/**
 * Returns a context specific to the current RPC call or <code>null</code> if not set.
 * The [RpcAuthContext] includes permissions.
 */
fun rpcContext(): RpcAuthContext? = CURRENT_RPC_CONTEXT.get()

internal class CurrentRpcContext : ThreadLocal<RpcAuthContext>() {

    override fun remove() {
        super.remove()
        MDC.clear()
    }

    override fun set(context: RpcAuthContext?) {
        when {
            context != null -> {
                super.set(context)
                // this is needed here as well because the Shell sets the context without going through the RpcServer
                //context.invocation.pushToLoggingContext()
            }
            else -> remove()
        }
    }
}