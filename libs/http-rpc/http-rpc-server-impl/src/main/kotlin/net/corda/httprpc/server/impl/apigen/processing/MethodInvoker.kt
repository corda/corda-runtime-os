package net.corda.httprpc.server.impl.apigen.processing

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.httprpc.security.CURRENT_RPC_CONTEXT
import net.corda.httprpc.server.impl.apigen.models.InvocationMethod
import net.corda.httprpc.server.impl.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.base.stream.Cursor
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

internal open class DefaultMethodInvoker(private val invocationMethod: InvocationMethod) :
    MethodInvoker {
    private companion object {
        private val log = contextLogger()
    }

    override fun invoke(vararg args: Any?): Any? {
        log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size}." }
        val instance = invocationMethod.instance
        val method = invocationMethod.method
        @Suppress("SpreadOperator")
        return when (args.size) {
            0 -> method.invoke(instance)
            else -> method.invoke(instance, *args)
        }.also { log.trace { "Invoke method \"${invocationMethod.method.name}\" with args size: ${args.size} completed." } }
    }
}

internal open class DurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod) :
    DefaultMethodInvoker(invocationMethod) {
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
        println("QQQ invokeDurableStreamMethod 1 ${args.size}")
        log.trace { """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size}.""" }
        require(args.isNotEmpty()) { throw IllegalArgumentException("Method returning Durable Streams was invoked without arguments.") }
        println("QQQ invokeDurableStreamMethod 2")

        val (durableContexts, methodArgs) = args.partition { it is DurableStreamContext }
        println("QQQ invokeDurableStreamMethod 3 durableContexts = $durableContexts; methodArgs = $methodArgs")
        if (durableContexts.size != 1) {
            println("QQQ invokeDurableStreamMethod 4 size = ${durableContexts.size}")
            val message =
                """Exactly one of the arguments is expected to be DurableStreamContext, actual: $durableContexts"""
            throw IllegalArgumentException(message)
        }
        val durableStreamContext = durableContexts.single() as DurableStreamContext
        println("QQQ invokeDurableStreamMethod 5 durableStreamContext = $durableStreamContext")

        val rpcAuthContext = CURRENT_RPC_CONTEXT.get() ?: throw FailedLoginException("Missing authentication context.")
        println("QQQ invokeDurableStreamMethod 6 rpcAuthContext = $rpcAuthContext")
        with(rpcAuthContext) {
            val rpcContextWithDurableStreamContext =
                this.copy(invocation = this.invocation.copy(durableStreamContext = durableStreamContext))
            CURRENT_RPC_CONTEXT.set(rpcContextWithDurableStreamContext)
        }
        println("QQQ invokeDurableStreamMethod 7 rpcAuthContext = $rpcAuthContext")

        @Suppress("SpreadOperator")
        val returnValue = super.invoke(*methodArgs.toTypedArray())
        println("QQQ invokeDurableStreamMethod 8 returnValue = $returnValue")

        val durableCursorTransferObject = uncheckedCast<Any, Supplier<Cursor.PollResult<Any>>>(returnValue as Any)
        println("QQQ invokeDurableStreamMethod 9 durableCursorTransferObject = $durableCursorTransferObject")
        return durableCursorTransferObject.get()
                .also {
                    println("QQQ invokeDurableStreamMethod 10 durableCursorTransferObject $it")
                    log.trace {
                        """Invoke durable streams method "${invocationMethod.method.name}" with args size: ${args.size} completed."""
                    }
                }
    }
}

internal class FiniteDurableStreamsMethodInvoker(private val invocationMethod: InvocationMethod) :
    DurableStreamsMethodInvoker(invocationMethod) {
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