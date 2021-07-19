package net.corda.flow.statemachine.impl


import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.data.crypto.SecureHash
import net.corda.data.flow.FlowError
import net.corda.data.flow.FlowKey
import net.corda.data.flow.RPCFlowResult
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.FlowSessionMessage
import net.corda.data.flow.event.RemoteFlowError
import net.corda.data.flow.event.Wakeup
import net.corda.flow.statemachine.FlowIORequest
import net.corda.flow.statemachine.FlowStateMachine
import net.corda.internal.di.DependencyInjectionService
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowException
import net.corda.v5.application.flows.FlowSession
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.Try
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.nio.ByteBuffer
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService

class TransientReference<out A>(@Transient val value: A)


@Suppress("LongParameterList")
class FlowStateMachineImpl<R>(
    override val clientId: String?,
    override val id: FlowKey,
    override val logic: Flow<R>,
    override val ourIdentity: Party,
    scheduler: FiberScheduler,
    override val creationTime: Long = System.currentTimeMillis()
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R> {

    companion object {
        /**
         * Return the current [FlowStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): FlowStateMachineImpl<*>? = Strand.currentStrand() as? FlowStateMachineImpl<*>

        private val log: Logger = LoggerFactory.getLogger("net.cordax.flow")

        private val SERIALIZER_BLOCKER =
            Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER").apply { isAccessible = true }.get(null)

    }

    data class TransientValues(
        val resultFuture: CompletableFuture<Any?>,
        val executor: ScheduledExecutorService,
        val checkpointSerializationService: SerializationService,
        val dependencyInjectionService: DependencyInjectionService,
        val clock: Clock
    ) : KryoSerializable {
        var suspended: CompletableFuture<SerializedBytes<FlowStateMachineImpl<*>>?> = CompletableFuture()
        val eventsOut = mutableListOf<FlowEvent>()

        override fun write(kryo: Kryo?, output: Output?) {
            throw IllegalStateException("${TransientValues::class.qualifiedName} should never be serialized")
        }

        override fun read(kryo: Kryo?, input: Input?) {
            throw IllegalStateException("${TransientValues::class.qualifiedName} should never be deserialized")
        }
    }

    data class TransientState(
        val suspendCount: Int,
        val ourIdentity: Party,
        val isKilled: Boolean,
//        val sessions: MutableMap<Trace.SessionId, FlowSessionImpl>,
//        val subFlows: MutableList<SubFlow>,
        val eventQueue: MutableList<FlowEvent>
    ) : KryoSerializable {
        override fun write(kryo: Kryo?, output: Output?) {
            throw IllegalStateException("${TransientValues::class.qualifiedName} should never be serialized")
        }

        override fun read(kryo: Kryo?, input: Input?) {
            throw IllegalStateException("${TransientValues::class.qualifiedName} should never be deserialized")
        }
    }

    private var transientValuesReference: TransientReference<TransientValues>? = null
    internal var transientValues: TransientValues
        // After the flow has been created, the transient values should never be null
        get() = transientValuesReference!!.value
        set(values) {
            check(transientValuesReference?.value == null) { "The transient values should only be set once when initialising a flow" }
            transientValuesReference = TransientReference(values)
        }

    private var transientStateReference: TransientReference<TransientState>? = null
    internal var transientState: TransientState
        // After the flow has been created, the transient state should never be null
        get() = transientStateReference!!.value
        set(state) {
            transientStateReference = TransientReference(state)
        }

    override val isKilled: Boolean get() = transientState.isKilled
    override val serializationService: SerializationService get() = transientValues.checkpointSerializationService
    override val resultFuture: CompletableFuture<R> get() = uncheckedCast(transientValues.resultFuture)
    override val logger = log

    private fun setLoggingContext() {
        MDC.put("flow-id", id.flowId)
        MDC.put("fiber-id", this.getId().toString())
        MDC.put("thread-id", Thread.currentThread().id.toString())
    }

    private fun Throwable.isUnrecoverable(): Boolean = this is VirtualMachineError && this !is StackOverflowError

    private fun logFlowError(throwable: Throwable) {
        logger.warn("Flow raised an error: ${throwable.message}. Sending it to flow hospital to be triaged.")
    }

    @Suspendable
    private fun initialiseFlow() {

    }

    @Suspendable
    override fun run() {
        setLoggingContext()

        logger.debug { "Calling flow: $logic" }
        val resultOrError = try {

            initialiseFlow()

            //TODO: we might need the sandbox class loader
            Thread.currentThread().contextClassLoader = logic.javaClass.classLoader

            suspend(FlowIORequest.ForceCheckpoint)

            val result = logic.call()

            Try.Success(result)
        } catch (t: Throwable) {
            if (t.isUnrecoverable()) {
                errorAndTerminate(
                    "Caught unrecoverable error from flow. Forcibly terminating the JVM, this might leave resources open, and most likely will.",
                    t
                )
            }
            logFlowError(t)
            Try.Failure<R>(t)
        }
        logger.info("flow ended $id")
        when (resultOrError) {
            is Try.Success -> {
                transientValues.resultFuture.complete(resultOrError.value)
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
                transientValues.resultFuture.completeExceptionally(resultOrError.exception)
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
        transientValues.suspended.complete(null)
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

}
