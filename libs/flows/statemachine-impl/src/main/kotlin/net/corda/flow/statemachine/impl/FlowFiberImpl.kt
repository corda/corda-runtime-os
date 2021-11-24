package net.corda.flow.statemachine.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.flow.statemachine.FlowContinuation
import net.corda.flow.statemachine.FlowFiber
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
import net.corda.flow.statemachine.requests.FlowIORequest
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.Try
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.MDC
import java.nio.ByteBuffer

class TransientReference<out A>(@Transient val value: A)

@Suppress("TooManyFunctions", "ComplexMethod", "LongParameterList")
class FlowFiberImpl<R>(
    private val clientId: String?,
    private val id: FlowKey,
    override val logic: Flow<R>,
    private val cpiId: String,
    private val flowName: String,
    scheduler: FiberScheduler
) : Fiber<Unit>(id.toString(), scheduler), FlowFiber<R> {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var nonSerializableStateReference: TransientReference<NonSerializableState>? = null
    private var nonSerializableState: NonSerializableState
        // After the flow has been created, the transient values should never be null
        get() = nonSerializableStateReference!!.value
        set(values) {
            check(nonSerializableStateReference?.value == null) { "The transient values should only be set once when initialising a flow" }
            nonSerializableStateReference = TransientReference(values)
        }
    private var housekeepingStateReference: TransientReference<HousekeepingState>? = null
    private var housekeepingState: HousekeepingState
        // After the flow has been created, the transient state should never be null
        get() = housekeepingStateReference!!.value
        set(state) {
            housekeepingStateReference = TransientReference(state)
        }

    val creationTime: Long = System.currentTimeMillis()

    private fun setLoggingContext() {
        MDC.put("flow-id", id.flowId)
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }

    private fun Throwable.isUnrecoverable(): Boolean = this is VirtualMachineError && this !is StackOverflowError

    private fun logFlowError(throwable: Throwable) {
        log.warn("Flow raised an error: ${throwable.message}")
    }

    @Suspendable
    override fun run() {
        setLoggingContext()
        log.debug { "Calling flow: $logic" }
        val resultOrError = executeFlowLogic()
        log.debug { "flow ended $id. isSuccess: ${resultOrError.isSuccess}" }

//        when (resultOrError) {
//            is Try.Success -> {
//                handleSuccess(resultOrError)
//            }
//            is Try.Failure -> {
//                handleFailure(resultOrError)
//            }
//        }
        housekeepingState = housekeepingState.copy(output = FlowIORequest.FlowFinished(resultOrError.getOrThrow()))
        housekeepingState.suspended.complete(null)
    }

    @Suspendable
    private fun executeFlowLogic(): Try<R> {
        return try {
            //TODOs: we might need the sandbox class loader
            Thread.currentThread().contextClassLoader = logic.javaClass.classLoader
            log.info("Executing flow and about to make initial checkpoint")
            suspend(FlowIORequest.ForceCheckpoint)
            log.info("Made initial checkpoint")
            val result = logic.call()
            Try.Success(result)
        } catch (t: Throwable) {
            if (t.isUnrecoverable()) {
                errorAndTerminate(
                    "Caught unrecoverable error from flow. Forcibly terminating the JVM, this might leave " +
                            "resources open, and most likely will.",
                    t
                )
            }
            logFlowError(t)
            Try.Failure(t)
        }
    }

//    private fun handleSuccess(resultOrError: Try.Success<R>) {
//        if (clientId != null) {
//            nonSerializableState.eventsOut += FlowEvent(
//                id,
//                cpiId,
//                RPCFlowResult(
//                    clientId,
//                    logic.javaClass.name,
//                    resultOrError.value.toString(),
//                    SecureHash(),
//                    null
//                )
//            )
//        }
//    }
//
//    private fun handleFailure(resultOrError: Try.Failure<R>) {
//        if (clientId != null) {
//            nonSerializableState.eventsOut += FlowEvent(
//                id,
//                cpiId,
//                RPCFlowResult(
//                    clientId,
//                    logic.javaClass.name,
//                    null,
//                    SecureHash(),
//                    ExceptionEnvelope(
//                        resultOrError.exception.cause.toString(),
//                        resultOrError.exception.message
//                    )
//                )
//            )
//        }
////        else if (transientState.initiatedBy != null) {
////            transientValues.eventsOut += RemoteFlowError(
////                id,
////                transientState.ourIdentity,
////                transientState.initiatedBy!!.counterparty,
////                transientState.initiatedBy!!.sourceSessionId,
////                transientState.initiatedBy!!.sequenceNo++,
////                resultOrError.exception.message ?: "remote error"
////            )
////        }
//    }

    @Suspendable
    override fun <SUSPENDRETURN : Any> suspend(request: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
        log.info("Suspend (${request::class.java.name}) $request")
        housekeepingState = housekeepingState.copy(output = request)
        parkAndSerialize { _, _ ->
            val fiberState = nonSerializableState.checkpointSerializer.serialize(this)
            housekeepingState.suspended.complete(fiberState)
        }
        setLoggingContext()
        return when (val outcome = housekeepingState.input) {
            is FlowContinuation.Run -> uncheckedCast(outcome.value)
            is FlowContinuation.Error -> throw outcome.exception
            else -> throw IllegalStateException("Tried to return when suspension outcome says to continue")
        }
    }

    override fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession {
        TODO("Not yet implemented")
    }

    override fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN {
        TODO("Not yet implemented")
    }

    override fun updateTimedFlowTimeout(timeoutSeconds: Long) {
        TODO("Not yet implemented")
    }

    override fun waitForCheckpoint(): Pair<Checkpoint, FlowIORequest<*>> {
        val checkpoint = housekeepingState.checkpoint.apply {
            fiber = ByteBuffer.wrap(housekeepingState.suspended.getOrThrow() ?: byteArrayOf())
        }
        return checkpoint to housekeepingState.output!!
    }

    override fun startFlow(): Fiber<Unit> = start()

    override fun nonSerializableState(nonSerializableState: NonSerializableState) {
        this.nonSerializableState = nonSerializableState
    }

    override fun housekeepingState(housekeepingState: HousekeepingState) {
        this.housekeepingState = housekeepingState
    }
}
