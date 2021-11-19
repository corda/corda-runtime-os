package net.corda.flow.statemachine.impl

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.ExceptionEnvelope
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.RPCFlowResult
import net.corda.data.flow.StateMachineState
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.statemachine.FlowIORequest
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.flow.statemachine.HousekeepingState
import net.corda.flow.statemachine.NonSerializableState
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
class FlowStateMachineImpl<R>(
    private val clientId: String?,
    private val id: FlowKey,
    private val logic: Flow<R>,
    private val cpiId: String,
    private val flowName: String,
    scheduler: FiberScheduler
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R> {

    companion object {
        private val log: Logger = contextLogger()
    }

    // This code is messy but I'm also the one that wrote the original code this is based on so its my fault.
    // Dno if there is a nicer way to write it (as I hope I would have done that originally if that was the case).
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

    val isKilled: Boolean get() = housekeepingState.isKilled
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

        // Never really liked the [Try] that was here but never bothered to remove it
        // Personally I would rather [Try] was deleted as it exists on the public API and is only really used by the state machine and RPC
        // code
        // All of this is just my opinion here though...
        when (resultOrError) {
            is Try.Success -> handleSuccess(resultOrError)
            is Try.Failure -> handleFailure(resultOrError)
        }
        // potentially this will change in the future to set the final future where the result is then put into a kafka topic for RPC to
        // retrieve results from, might need some changes so failures can be handled too. This might have handled in the code above already.
        nonSerializableState.suspended.complete(null)
    }

    // Rename to [executeFlow] or [runFlow]
    @Suspendable
    private fun executeFlowLogic(): Try<R> {
        return try {
            //TODOs: we might need the sandbox class loader
            Thread.currentThread().contextClassLoader = logic.javaClass.classLoader
            // Did we add this for a reason? If so why, so we know if we need to keep or remove it at some point.
            suspend(FlowIORequest.ForceCheckpoint)
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

    private fun handleSuccess(resultOrError: Try.Success<R>) {
        if (clientId != null) {
            nonSerializableState.eventsOut += FlowEvent(
                id,
                cpiId,
                RPCFlowResult(
                    clientId,
                    logic.javaClass.name,
                    // Should be the JSON string
                    resultOrError.value.toString(),
                    SecureHash(),
                    null
                )
            )
        }
    }

    private fun handleFailure(resultOrError: Try.Failure<R>) {
        if (clientId != null) {
            nonSerializableState.eventsOut += FlowEvent(
                id,
                cpiId,
                RPCFlowResult(
                    clientId,
                    logic.javaClass.name,
                    null,
                    SecureHash(),
                    // How does the exception envelope work for peers?
                    // I thought we were sticking with throwing exceptions on peer nodes, which we were sending across before as a serialized
                    // blob. We could also send the class name and construct a new one via reflection (exceptions must provide certain
                    // constructors in this case though.
                    // Or is the [ExceptionEnvelope] only for sending errors back as results of flows to rpc clients? In which case it
                    // provides enough information as it is.
                    ExceptionEnvelope(
                        resultOrError.exception.cause.toString(),
                        resultOrError.exception.message
                    )
                )
            )
        }
//        else if (transientState.initiatedBy != null) {
//            transientValues.eventsOut += RemoteFlowError(
//                id,
//                transientState.ourIdentity,
//                transientState.initiatedBy!!.counterparty,
//                transientState.initiatedBy!!.sourceSessionId,
//                transientState.initiatedBy!!.sequenceNo++,
//                resultOrError.exception.message ?: "remote error"
//            )
//        }
    }

    @Suspendable
    override fun <SUSPENDRETURN : Any> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
        log.info("suspend $ioRequest")
        sendEvents(ioRequest)
        while (true) {
            val (ret, suspend) = processEvent(ioRequest)
            if (!suspend) {
                log.info("suspend wakeup $ioRequest")
                return uncheckedCast(ret)
            }
            parkAndSerialize { _, _ ->
                val fiberState = nonSerializableState.checkpointSerializer.serialize(this)
                nonSerializableState.suspended.complete(fiberState)
            }
            // Does it matter that the logging context is set after completing the suspended future? I assume not because a different thread
            // must be waiting on the future
            // It is inside the while loop though which implies the next loop will have the context set and will log differently than the
            // first run through
            setLoggingContext()
        }
    }

    // really need to start extracting this code out now because its going to become very big
    private fun sendEvents(ioRequest: FlowIORequest<*>) {
        log.info("sendEvents $ioRequest")
        when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                nonSerializableState.eventsOut += FlowEvent(
                    id,
                    cpiId,
                    Wakeup(flowName)
                )
            }
            is FlowIORequest.CloseSessions -> TODO()
            is FlowIORequest.GetFlowInfo -> TODO()
            is FlowIORequest.Receive -> TODO()
            is FlowIORequest.Send -> TODO()
            is FlowIORequest.SendAndReceive -> TODO()
            is FlowIORequest.Sleep -> TODO()
            is FlowIORequest.WaitForSessionConfirmations -> TODO()
        }
    }

    // this isn't really processing an event
    // e.g. we need to handle incoming data messages from peers while keeping the io request the same to know the send we're trying to complete
    // really need to start extracting this code out now because its going to become very big
    private fun processEvent(ioRequest: FlowIORequest<*>): Pair<Any?, Boolean> {
        log.info("processEvent $ioRequest")
        return when (ioRequest) {
            is FlowIORequest.ForceCheckpoint -> {
                val wakeup = housekeepingState.eventsIn.firstOrNull { it.payload is Wakeup }
                if (wakeup != null) {
                    housekeepingState.eventsIn.remove(wakeup)
                }
                Pair(Unit, (wakeup == null))
            }
            is FlowIORequest.CloseSessions -> TODO()
            is FlowIORequest.GetFlowInfo -> TODO()
            is FlowIORequest.Send -> TODO()
            is FlowIORequest.Receive -> TODO()
            is FlowIORequest.SendAndReceive -> TODO()
            is FlowIORequest.Sleep -> TODO()
            is FlowIORequest.WaitForSessionConfirmations -> TODO()
        }
    }

    // can we make this a [FlowIORequest]?
    // it wasn't in C4 but doesn't mean we couldn't make it do it, although [initiateFlow] doesn't suspend the fiber
    // so it might not fit it correctly?
    override fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession {
        TODO("Not yet implemented")
    }

    // same as the above, make subFlow a [FlowIORequest] - Maybe we can have an interface that sits on [FlowIORequest]
    // then we can have a sealed class for the existing [FlowIORequest] (meaning suspending operations) and another for non-suspending operations
    override fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN {
        TODO("Not yet implemented")
    }

    // Update timed flow timeout is basically a hack into the underlying timeout service (at least with how it was implemented in C4)
    override fun updateTimedFlowTimeout(timeoutSeconds: Long) {
        TODO("Not yet implemented")
    }

    // makes sense as an API
    override fun waitForCheckpoint(): Pair<Checkpoint?, List<FlowEvent>> {
        val fibreState = nonSerializableState.suspended.getOrThrow() ?: return Pair(null, emptyList())

        return Pair(
            Checkpoint(
                id,
                ByteBuffer.wrap(fibreState),
                buildStateMachineState()
            ),
            nonSerializableState.eventsOut
        )
    }

    private fun buildStateMachineState(): StateMachineState {
        return StateMachineState(
            clientId,
            housekeepingState.suspendCount,
            housekeepingState.isKilled,
            ByteBuffer.wrap(clientId?.toByteArray()),
            housekeepingState.eventsIn
        )
    }

    // is there a reason this isn't called [start] and have it override [Fiber.start]? Only a small thing though
    override fun startFlow(): Fiber<Unit> = start()

    override fun nonSerializableState(nonSerializableState: NonSerializableState) {
        this.nonSerializableState = nonSerializableState
    }

    override fun housekeepingState(housekeepingState: HousekeepingState) {
        this.housekeepingState = housekeepingState
    }

    // Just make this a `val flow`
    override fun getFlowLogic(): Flow<R> {
        return logic
    }

}
