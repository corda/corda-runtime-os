package net.corda.flow.manager.impl.fiber

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.FlowKey
import net.corda.flow.manager.fiber.FlowContinuation
import net.corda.flow.manager.fiber.FlowFiber
import net.corda.flow.manager.fiber.FlowFiberExecutionContext
import net.corda.flow.manager.fiber.FlowIORequest
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowId
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.MDC
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@Suppress("TooManyFunctions", "ComplexMethod", "LongParameterList")
class FlowFiberImpl<R>(
    override val flowId: FlowId,
    override val flowKey: FlowKey,
    override val flowLogic: Flow<R>,
    scheduler: FiberScheduler
) : Fiber<Unit>(flowKey.toString(), scheduler), FlowFiber<R> {

    companion object {
        private val log: Logger = contextLogger()
    }

    @Transient
    private var flowFiberExecutionContext: FlowFiberExecutionContext?=null

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

        try {
            suspend(FlowIORequest.ForceCheckpoint)
            val result = flowLogic.call()
            flowCompletion.complete(FlowIORequest.FlowFinished(result))
        } catch (t: Throwable) {
            log.error("Flow failed", t)
            if (t.isUnrecoverable()) {
                errorAndTerminate(
                    "Caught unrecoverable error from flow. Forcibly terminating the JVM, this might leave " +
                            "resources open, and most likely will.",
                    t
                )
            }

            flowCompletion.complete(FlowIORequest.FlowFailed(t))
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
        }catch (e:Throwable){
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

    private fun Throwable.isUnrecoverable(): Boolean = this is VirtualMachineError && this !is StackOverflowError

    private fun initialiseThreadContext() {
        Thread.currentThread().contextClassLoader = flowLogic.javaClass.classLoader
    }

    private fun setLoggingContext(){
        MDC.put("flow-id", flowKey.toString())
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }
}
