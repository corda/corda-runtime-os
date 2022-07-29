package net.corda.flow.fiber

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.fibers.FiberWriter
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.flow.fiber.FlowFiberImpl.SerializableFiberWriter
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.MDC
import java.io.Serializable
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class FlowFiberImpl(
    override val flowId: UUID,
    override val flowLogic: FlowLogicAndArgs,
    scheduler: FiberScheduler
) : Fiber<Unit>(flowId.toString(), scheduler), FlowFiber, Interruptable {

    private fun interface SerializableFiberWriter : FiberWriter, Serializable

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
        // Ensure run() does not exit via any means without completing the future, in order not to indefinitely block
        // the flow event pipeline. Note that this is executed in a Quasar concurrent executor thread and Throwables are
        // consumed by that too, so if they are rethrown from here we do not get process termination or any other form
        // of critical error handling for free, only undefined behaviour.
        try {
            runFlow()
        } catch (t: Throwable) {
            log.error("FlowFiber failed due to internal Throwable being thrown", t)
            failTopLevelSubFlow(t)
        }

        if (!flowCompletion.isDone) {
            log.error("runFlow failed to complete normally, forcing a failure")
            failTopLevelSubFlow(IllegalStateException("Flow failed to complete normally, forcing a failure"))
        }
    }

    @Suspendable
    private fun runFlow() {
        initialiseThreadContext()
        setLoggingContext()
        suspend(FlowIORequest.InitialCheckpoint)

        val outcomeOfFlow = try {
            log.info("Flow starting.")
            FlowIORequest.FlowFinished(flowLogic.invoke())
        } catch (t: Throwable) {
            log.error("Flow failed", t)
            FlowIORequest.FlowFailed(t)
        }

        when (outcomeOfFlow) {
            is FlowIORequest.FlowFinished -> finishTopLevelSubFlow(outcomeOfFlow)
            is FlowIORequest.FlowFailed -> failTopLevelSubFlow(outcomeOfFlow.exception)
            else -> throw IllegalStateException("Unexpected Flow outcome")
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
        parkAndSerialize(SerializableFiberWriter { _, _ ->
            log.info("Parking...")
            val fiberState = getExecutionContext().sandboxGroupContext.checkpointSerializer.serialize(this)
            flowCompletion.complete(FlowIORequest.FlowSuspended(ByteBuffer.wrap(fiberState), request))
            log.info("Parked.")
        })

        setLoggingContext()
        log.info("Flow resuming.")

        @Suppress("unchecked_cast")
        return when (val outcome = suspensionOutcome!!) {
            is FlowContinuation.Run -> outcome.value as SUSPENDRETURN
            is FlowContinuation.Error -> throw outcome.exception.fillInStackTrace()
            else -> throw IllegalStateException("Tried to return when suspension outcome says to continue")
        }
    }

    @Suspendable
    private fun <T : FlowIORequest<*>> finishTopLevelSubFlow(outcomeOfFlow: T) {
        // We close the sessions here, which delegates to the subFlow finished request handler, rather than combining the logic into the
        // flow finish request handler. This is due to the flow finish code removing the flow's checkpoint, which is needed by the close
        // logic to determine whether all sessions have successfully acknowledged receipt of the close messages.
        val flowStackItem = getRemainingFlowStackItem()
        if (flowStackItem.sessionIds.isNotEmpty()) {
            suspend(FlowIORequest.SubFlowFinished(flowStackItem))
        }
        flowCompletion.complete(outcomeOfFlow)
    }

    @Suspendable
    private fun failTopLevelSubFlow(throwable: Throwable) {
        // We close the sessions here, which delegates to the subFlow failed request handler, rather than combining the logic into the
        // flow finish request handler. This is due to the flow finish code removing the flow's checkpoint, which is needed by the close
        // logic to determine whether all sessions have successfully acknowledged receipt of the close messages.
        val flowStackItem = getRemainingFlowStackItem()
        if (flowStackItem.sessionIds.isNotEmpty()) {
            suspend(FlowIORequest.SubFlowFailed(throwable, flowStackItem))
        }
        flowCompletion.complete(FlowIORequest.FlowFailed(throwable))
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
        MDC.put("fiber-id", id.toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }

    override fun attemptInterrupt() {
        // Contract of Interruptable is that this method should be thread safe, do not call anything here that isn't
        interrupt()
    }
}
