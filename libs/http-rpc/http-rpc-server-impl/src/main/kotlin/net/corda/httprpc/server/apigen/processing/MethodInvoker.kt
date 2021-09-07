package net.corda.httprpc.server.apigen.processing

import net.corda.ext.internal.rpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.server.apigen.models.InvocationMethod
import net.corda.httprpc.server.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.internal.application.executeWithThreadContextClassLoader
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.base.stream.Cursor
import net.corda.internal.base.stream.DurableStreamContext
import net.corda.v5.base.util.contextLogger
import java.lang.IllegalArgumentException
import net.corda.v5.base.util.trace
import java.util.function.Supplier
import javax.security.auth.login.FailedLoginException

/**
 * [MethodInvoker] implementations are responsible for doing method invocations using the arguments provided.
 *
 */
interface MethodInvoker {
    fun invoke(vararg args: Any?): Any?
}

internal open class DefaultMethodInvoker(private val invocationMethod: InvocationMethod, private val cl: ClassLoader) : MethodInvoker {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): Any? {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        val instance = invocationMethod.instance
        val method = invocationMethod.method
        @Suppress("SpreadOperator")
        return when (args.size) {
            0 -> executeWithThreadContextClassLoader(cl) { method.invoke(instance) }
            else -> executeWithThreadContextClassLoader(cl) { method.invoke(instance, *args) }
        }.also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }
}

internal open class DurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod, cl: ClassLoader) : DefaultMethodInvoker(invocationMethod, cl) {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): DurableReturnResult<Any> {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        @Suppress("SpreadOperator")
        val pollResult = invokeDurableStreamMethod(*args)

        return DurableReturnResult(
                pollResult.positionedValues,
                pollResult.remainingElementsCountEstimate
        ).also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }

    @Suppress("ThrowsCount")
    internal fun invokeDurableStreamMethod(vararg args: Any?): Cursor.PollResult<Any> {
        log.trace { """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size}.""" }
        require(args.isNotEmpty()) { throw IllegalArgumentException("Method returning Durable Streams was invoked without arguments.") }

        val (durableContexts, methodArgs) = args.partition { it is DurableStreamContext }
        if (durableContexts.size != 1) {
            val message = """Exactly one of the arguments is expected to be DurableStreamContext, actual: $durableContexts"""
            throw IllegalArgumentException(message)
        }
        val durableStreamContext = durableContexts.single() as DurableStreamContext

        val rpcAuthContext = CURRENT_RPC_CONTEXT.get() ?: throw FailedLoginException("Missing authentication context.")
        with(rpcAuthContext) {
            val rpcContextWithDurableStreamContext = this.copy(invocation = this.invocation.copy(durableStreamContext = durableStreamContext))
            CURRENT_RPC_CONTEXT.set(rpcContextWithDurableStreamContext)
        }

        @Suppress("SpreadOperator")
        val returnValue = super.invoke(*methodArgs.toTypedArray())

        val durableCursorTransferObject = uncheckedCast<Any, Supplier<Cursor.PollResult<Any>>>(returnValue as Any)
        return durableCursorTransferObject.get()
                .also { log.trace { """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size} completed.""" } }
    }
}

internal class FiniteDurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod, cl: ClassLoader) : DurableStreamsMethodInvoker(invocationMethod, cl) {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): FiniteDurableReturnResult<Any> {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        @Suppress("SpreadOperator")
        val pollResult = invokeDurableStreamMethod(*args)
        return FiniteDurableReturnResult(
            pollResult.positionedValues,
            pollResult.remainingElementsCountEstimate,
            pollResult.isLastResult
        ).also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }
}