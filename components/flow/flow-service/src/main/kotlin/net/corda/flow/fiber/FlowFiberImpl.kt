package net.corda.flow.fiber

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowStackItem
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.MDC
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@Suppress("TooManyFunctions", "ComplexMethod", "LongParameterList")
class FlowFiberImpl<R>(
    override val flowId: UUID,
    override val flowLogic: Flow<R>,
    scheduler: FiberScheduler
) : Fiber<Unit>(flowId.toString(), scheduler), FlowFiber<R> {

    companion object {
        private val log: Logger = contextLogger()
    }

    @Transient
    private var flowFiberExecutionContext: FlowFiberExecutionContext? = null

    @Transient
    private var flowCompletion = CompletableFuture<FlowIORequest<*>>()

    @Transient
    var suspensionOutcome: FlowContinuation? = null

    override fun getExecutionContext(): FlowFiberExecutionContext {
        return flowFiberExecutionContext!!
    }

    @Suspendable
    override fun startFlow(flowFiberExecutionContext: FlowFiberExecutionContext): Future<FlowIORequest<*>> {
        this.flowFiberExecutionContext = flowFiberExecutionContext

        start()
        return flowCompletion
    }

    @Suspendable
    override fun run() {
        initialiseThreadContext()
        setLoggingContext()
        log.info("Flow starting.")

        val result = try {
            suspend(FlowIORequest.InitialCheckpoint)

            /**
             * TODOs: Need to review/discuss how/where to ensure the user code can only return
             * a string
             */
            when (val result = flowLogic.call()) {
                is String -> FlowIORequest.FlowFinished(result)
                else -> throw IllegalStateException("The flow result has to be a string.")
            }
        } catch (t: Throwable) {
            log.error("Flow failed", t)
            if (t.isUnrecoverable()) {
                errorAndTerminate(
                    "Caught unrecoverable error from flow. Forcibly terminating the JVM, this might leave " +
                            "resources open, and most likely will.",
                    t
                )
            }

            FlowIORequest.FlowFailed(t)
        }

        try {
            closeSessions()
            flowCompletion.complete(result)
        } catch (e: CordaRuntimeException) {
            flowCompletion.complete(FlowIORequest.FlowFailed(e))
        }
    }

    override fun resume(
        flowFiberExecutionContext: FlowFiberExecutionContext,
        suspensionOutcome: FlowContinuation,
        scheduler: FiberScheduler
    ): Future<FlowIORequest<*>> {
        this.flowFiberExecutionContext = flowFiberExecutionContext
        this.suspensionOutcome = suspensionOutcome
        this.flowCompletion = CompletableFuture<FlowIORequest<*>>()
        unparkDeserialized(this, scheduler)
        return flowCompletion
    }

    @Suspendable
    override fun <SUSPENDRETURN> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
        log.info("Flow suspending.")
        try {
            parkAndSerialize { _, _ ->
                log.info("Parking...")
                val fiberState = getExecutionContext().checkpointSerializer.serialize(this)
                flowCompletion.complete(FlowIORequest.FlowSuspended(ByteBuffer.wrap(fiberState), request))
                log.info("Parked.")
            }
        } catch (e: Throwable) {
            throw e
        }

        setLoggingContext()
        log.info("Flow resuming.")

        return when (val outcome = suspensionOutcome!!) {
            is FlowContinuation.Run -> uncheckedCast(outcome.value)
            is FlowContinuation.Error -> throw outcome.exception
            else -> throw IllegalStateException("Tried to return when suspension outcome says to continue")
        }
    }

    @Suspendable
    private fun closeSessions() {
        // We close the sessions here, which delegates to the close session request handler, rather than combining the close logic into the
        // flow finish request handler. This is due to the flow finish code removing the flow's checkpoint, which is needed by the close
        // logic to determine whether all sessions have successfully acknowledged receipt of the close messages.
        val flowStackItem = getRemainingFlowStackItem()
        if (flowStackItem.sessionIds.isNotEmpty()) {
            suspend(FlowIORequest.CloseSessions(flowStackItem.sessionIds.toSet()))
        }
    }

    @Suppress("ThrowsCount")
    private fun getRemainingFlowStackItem(): FlowStackItem {
        val flowStackService = flowFiberExecutionContext?.flowStackService
        return when {
            flowStackService == null -> {
                log.info("Flow [$flowId] should have a single flow stack item when finishing but the stack was null")
                throw CordaRuntimeException("Flow [$flowId] should have a single flow stack item when finishing but the stack was null")
            }
            flowStackService.size > 1 -> {
                log.info(
                    "Flow [$flowId] should have a single flow stack item when finishing but contained the following elements instead: " +
                            "${flowFiberExecutionContext?.flowStackService}"
                )
                throw CordaRuntimeException(
                    "Flow [$flowId] should have a single flow stack item when finishing but contained " +
                            "${flowFiberExecutionContext?.flowStackService?.size} elements"
                )
            }
            flowStackService.size == 0 -> {
                log.info("Flow [$flowId] should have a single flow stack item when finishing but was empty")
                throw CordaRuntimeException("Flow [$flowId] should have a single flow stack item when finishing but was empty")
            }
            else -> {
                when (val item = flowStackService.peek()) {
                    null -> {
                        log.info("Flow [$flowId] should have a single flow stack item when finishing but was empty")
                        throw CordaRuntimeException("Flow [$flowId] should have a single flow stack item when finishing but was empty")
                    }
                    else -> item
                }
            }
        }
    }

    private fun Throwable.isUnrecoverable(): Boolean = this is VirtualMachineError && this !is StackOverflowError

    private fun initialiseThreadContext() {
        Thread.currentThread().contextClassLoader = flowLogic.javaClass.classLoader
    }

    private fun setLoggingContext() {
        MDC.put("flow-id", flowId.toString())
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }
}
