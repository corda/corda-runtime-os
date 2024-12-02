package net.corda.rest.server.impl.apigen.processing

import net.corda.lifecycle.Lifecycle
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.rest.server.impl.apigen.models.InvocationMethod
import net.corda.utilities.trace
import org.slf4j.LoggerFactory

/**
 * [MethodInvoker] implementations are responsible for doing method invocations using the arguments provided.
 *
 */
interface MethodInvoker {
    fun invoke(vararg args: Any?): Any?
}

internal open class DefaultMethodInvoker(private val invocationMethod: InvocationMethod) :
    MethodInvoker {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun invoke(vararg args: Any?): Any? {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        val instance = invocationMethod.instance

        if ((instance as? Lifecycle)?.isRunning == false) {
            "${instance.javaClass.simpleName} is not running. Unable to invoke \"${invocationMethod.method.name}\".".let {
                log.warn(it)
                throw ServiceUnavailableException(it)
            }
        }

        val method = invocationMethod.method

        @Suppress("SpreadOperator")
        val invoked = when (args.size) {
            0 -> method.invoke(instance)
            else -> method.invoke(instance, *args)
        }.also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
        return if (invocationMethod.transform != null) {
            invocationMethod.transform.invoke(invoked)
        } else {
            invoked
        }
    }
}
