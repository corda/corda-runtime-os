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
import net.corda.flow.statemachine.requests.FlowAsyncRequest
import net.corda.flow.statemachine.requests.FlowAsyncResponse
import net.corda.flow.statemachine.requests.OutputEvent
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
import java.util.UUID

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

        when (resultOrError) {
            is Try.Success -> {
                handleSuccess(resultOrError)
            }
            is Try.Failure -> {
                handleFailure(resultOrError)
            }
        }
        nonSerializableState.suspended.complete(null)
    }

    @Suspendable
    private fun executeFlowLogic(): Try<R> {
        return try {
            //TODOs: we might need the sandbox class loader
            Thread.currentThread().contextClassLoader = logic.javaClass.classLoader
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
            nonSerializableState.eventsOut += OutputEvent(
                key = id,
                to = nonSerializableState.flowEventTopic,
                FlowEvent(
                    id,
                    cpiId,
                    RPCFlowResult(
                        clientId,
                        logic.javaClass.name,
                        resultOrError.value.toString(),
                        SecureHash(),
                        null
                    )
                )
            )
        }
    }

    private fun handleFailure(resultOrError: Try.Failure<R>) {
        if (clientId != null) {
            nonSerializableState.eventsOut += OutputEvent(
                key = id,
                to = nonSerializableState.flowEventTopic,
                FlowEvent(
                    id,
                    cpiId,
                    RPCFlowResult(
                        clientId,
                        logic.javaClass.name,
                        null,
                        SecureHash(),
                        ExceptionEnvelope(
                            resultOrError.exception.cause.toString(),
                            resultOrError.exception.message
                        )
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
    override fun <SUSPENDRETURN> suspend(ioRequest: FlowIORequest<SUSPENDRETURN>): SUSPENDRETURN {
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
            setLoggingContext()
        }
    }

    private fun sendEvents(ioRequest: FlowIORequest<*>) {
        log.info("sendEvents $ioRequest")
        when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                nonSerializableState.eventsOut += OutputEvent(
                    key = id, to = nonSerializableState.flowEventTopic, FlowEvent(
                        id,
                        cpiId,
                        Wakeup(flowName)
                    )
                )
            }
            is FlowIORequest.CloseSessions -> TODO()
            is FlowIORequest.GetFlowInfo -> TODO()
            is FlowIORequest.Receive -> TODO()
            is FlowIORequest.Send -> TODO()
            is FlowIORequest.SendAndReceive -> TODO()
            is FlowIORequest.Sleep -> TODO()
            is FlowIORequest.WaitForSessionConfirmations -> TODO()
            is FlowAsyncRequest<*, *> -> {
                // this payload is an avro object defined in the service that made this suspending request
                // don't think the partition that these events go to matters, but we could set the key based on the flow id is we wanted
                // the key depends on the key type of the topic the event is being sent to
                nonSerializableState.eventsOut +=  OutputEvent(key = UUID.randomUUID(), to = ioRequest.to, ioRequest.payload)
            }
            else -> throw IllegalArgumentException("Unrecognised request type ${ioRequest.javaClass.name}")
        }
    }

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
            is FlowAsyncRequest<*, *> -> {
                val response = housekeepingState.eventsIn.firstOrNull { it.payload is FlowAsyncResponse }
                if (response != null) {
                    housekeepingState.eventsIn.remove(response)
                    ioRequest.response((response.payload as FlowAsyncResponse).response) to true
                } else {
                    Unit to false
                }
            }
            else -> throw IllegalArgumentException("Unrecognised request type ${ioRequest.javaClass.name}")
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

    override fun waitForCheckpoint(): Pair<Checkpoint?, List<OutputEvent>> {
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

    override fun startFlow(): Fiber<Unit> = start()

    override fun nonSerializableState(nonSerializableState: NonSerializableState) {
        this.nonSerializableState = nonSerializableState
    }

    override fun housekeepingState(housekeepingState: HousekeepingState) {
        this.housekeepingState = housekeepingState
    }

    override fun getFlowLogic(): Flow<R> {
        return logic
    }

}
