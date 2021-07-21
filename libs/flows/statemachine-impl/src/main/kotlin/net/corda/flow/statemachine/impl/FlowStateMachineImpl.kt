package net.corda.flow.statemachine.impl


import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowError
import net.corda.data.flow.FlowKey
import net.corda.data.flow.RPCFlowResult
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.Wakeup
import net.corda.flow.statemachine.FlowIORequest
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.Try
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import org.slf4j.Logger
import org.slf4j.MDC

class TransientReference<out A>(@Transient val value: A)


@Suppress("ForbiddenComment", "ComplexMethod", "TooGenericExceptionCaught")
class FlowStateMachineImpl<R>(
    override val clientId: String?,
    override val id: FlowKey,
    override val logic: Flow<R>,
    val ourIdentity: Party,
    scheduler: FiberScheduler
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R> {

    companion object {
        private val log: Logger = contextLogger()
    }

    private var transientValuesReference: TransientReference<TransientValues>? = null
    var transientValues: TransientValues
        // After the flow has been created, the transient values should never be null
        get() = transientValuesReference!!.value
        set(values) {
            check(transientValuesReference?.value == null) { "The transient values should only be set once when initialising a flow" }
            transientValuesReference = TransientReference(values)
        }

    private var transientStateReference: TransientReference<TransientState>? = null
    var transientState: TransientState
        // After the flow has been created, the transient state should never be null
        get() = transientStateReference!!.value
        set(state) {
            transientStateReference = TransientReference(state)
        }

    val isKilled: Boolean get() = transientState.isKilled
    val creationTime: Long = System.currentTimeMillis()

    private fun setLoggingContext() {
        MDC.put("flow-id", id.flowId)
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }

    private fun Throwable.isUnrecoverable(): Boolean = this is VirtualMachineError && this !is StackOverflowError

    private fun logFlowError(throwable: Throwable) {
        log.warn("Flow raised an error: ${throwable.message}. Sending it to flow hospital to be triaged.")
    }

    @Suspendable
    override fun run() {
        setLoggingContext()
        log.debug { "Calling flow: $logic" }
        val resultOrError = try {
            //TODO: we might need the sandbox class loader
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

        log.info("flow ended $id")
        when (resultOrError) {
            is Try.Success -> {
                val clientId = clientId
                if (clientId != null) {
                    transientValues.eventsOut += FlowEvent(
                        id,
                        RPCFlowResult(
                            clientId,
                            logic.javaClass.name,
                            resultOrError.value.toString(),
                            SecureHash(),
                            null
                        )
                    )
                }
            }
            is Try.Failure -> {
                val clientId = clientId
                if (clientId != null) {
                    transientValues.eventsOut += FlowEvent(
                        id,
                        RPCFlowResult(
                            clientId,
                            logic.javaClass.name,
                            null,
                            SecureHash(),
                            FlowError(
                                resultOrError.exception.cause.toString(),
                                resultOrError.exception.message
                            )
                        )
                    )
                }
//                } else if (transientState.initiatedBy != null) {
//                    transientValues.eventsOut += RemoteFlowError(
//                        id,
//                        transientState.ourIdentity,
//                        transientState.initiatedBy!!.counterparty,
//                        transientState.initiatedBy!!.sourceSessionId,
//                        transientState.initiatedBy!!.sequenceNo++,
//                        resultOrError.exception.message ?: "remote error"
//                    )
//                }
            }
        }
        transientValues.suspended = null
    }

    @Suspendable
    override fun <SUSPENDRETURN : Any> suspend(
        ioRequest: FlowIORequest<SUSPENDRETURN>
    ): SUSPENDRETURN {
        log.info("suspend $ioRequest")
        sendEvents(ioRequest)
        while (true) {
            val (ret, suspend) = processEvent(ioRequest)
            if (!suspend) {
                log.info("suspend wakeup $ioRequest")
                return uncheckedCast(ret)
            }
            parkAndSerialize { _, _ ->
                val fiberState = transientValues.checkpointSerializationService.serialize(this)
                transientValues.suspended = uncheckedCast(fiberState)
            }
            setLoggingContext()
        }
    }

    private fun sendEvents(
        ioRequest: FlowIORequest<*>
    ) {
        log.info("sendEvents $ioRequest")
        when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                transientValues.eventsOut += FlowEvent(
                    id,
                    Wakeup()
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

    private fun processEvent(
        ioRequest: FlowIORequest<*>
    ): Pair<Any?, Boolean> {
        log.info("processEvent $ioRequest")
        return when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                val wakeup = transientState.eventQueue.firstOrNull { it is Wakeup }
                if (wakeup != null) {
                    transientState.eventQueue.remove(wakeup)
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
            else -> throw IllegalArgumentException("unrecognised IOREQUEST type ${ioRequest.javaClass.name}")
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

    override fun waitForCheckpoint(): Checkpoint {
        TODO("Not yet implemented")
    }

    override fun startFlow(): Fiber<Unit> = start()

}
