package net.corda.rest.security

import org.slf4j.MDC

data class RestAuthContext(
    val invocation: InvocationContext,
    private val authorizer: AuthorizingSubject
) : AuthorizingSubject by authorizer


@JvmField
val CURRENT_REST_CONTEXT: ThreadLocal<RestAuthContext> = CurrentRestContext()

/**
 * Returns a context specific to the current rest call or <code>null</code> if not set.
 * The [RestAuthContext] includes permissions.
 */
fun restContext(): RestAuthContext? = CURRENT_REST_CONTEXT.get()

internal class CurrentRestContext : ThreadLocal<RestAuthContext>() {

    override fun remove() {
        super.remove()
        MDC.clear()
    }

    override fun set(context: RestAuthContext?) {
        when {
            context != null -> {
                super.set(context)
                // this is needed here as well because the Shell sets the context without going through the RestServer
                //context.invocation.pushToLoggingContext()
            }
            else -> remove()
        }
    }
}