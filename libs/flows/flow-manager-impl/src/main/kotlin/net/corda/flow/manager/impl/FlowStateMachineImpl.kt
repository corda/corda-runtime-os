package net.corda.flow.manager.impl


import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.FiberScheduler
import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoSerializable
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.ext.internal.logging.context.pushToLoggingContext
import net.corda.flow.manager.*
import net.corda.internal.application.context.InvocationContext
import net.corda.internal.di.DependencyInjectionService
import net.corda.internal.di.FlowStateMachineInjectable
import net.corda.v5.application.flows.Destination
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowException
import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.context.Trace
import net.corda.v5.base.internal.castIfPossible
import net.corda.v5.base.internal.concurrent.OpenFuture
import net.corda.v5.base.internal.concurrent.openFuture
import net.corda.v5.base.internal.uncheckedCast
import net.corda.v5.base.util.Try
import net.corda.v5.base.util.debug
import net.corda.v5.serialization.SerializedBytes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService

class TransientReference<out A>(@Transient val value: A)

class FlowStateMachineImpl<R>(
    override val clientId: String?,
    override val id: StateMachineRunId,
    override val logic: Flow<R>,
    scheduler: FiberScheduler,
    override val creationTime: Long = System.currentTimeMillis()
) : Fiber<Unit>(id.toString(), scheduler), FlowStateMachine<R>, FlowStateMachineInjectable {
    companion object {
        /**
         * Return the current [FlowStateMachineImpl] or null if executing outside of one.
         */
        fun currentStateMachine(): FlowStateMachineImpl<*>? = Strand.currentStrand() as? FlowStateMachineImpl<*>

        private val log: Logger = LoggerFactory.getLogger("net.cordax.flow")

        private val SERIALIZER_BLOCKER =
            Fiber::class.java.getDeclaredField("SERIALIZER_BLOCKER").apply { isAccessible = true }.get(null)

    }

    /**
     * Return the logger for this state machine. The logger name incorporates [id] and so including it in the log message
     * is not necessary.
     */
    override val logger = log

    data class TransientValues(
        val resultFuture: OpenFuture<Any?>,
        val executor: ScheduledExecutorService,
        val checkpointSerializationService: SerializationService,
        val dependencyInjectionService: DependencyInjectionService,
        val flowFactory: FlowFactory,
        val clock: Clock
    ) : KryoSerializable {
        val suspended: OpenFuture<SerializedBytes<FlowStateMachineImpl<*>>?> = openFuture()
        val eventsOut = mutableListOf<FlowEvent>()

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

    private var transientStateReference: TransientReference<FlowState>? = null
    internal var transientState: FlowState
        // After the flow has been created, the transient state should never be null
        get() = transientStateReference!!.value
        set(state) {
            transientStateReference = TransientReference(state)
        }


    override val context: InvocationContext get() = transientState.context
    override val ourIdentity: Party get() = transientState.ourIdentity
    override val isKilled: Boolean get() = transientState.isKilled
    override val clock: Clock get() = transientValues.clock
    override val executor: ScheduledExecutorService get() = transientValues.executor
    override val serializationService: SerializationService get() = transientValues.checkpointSerializationService
    override val resultFuture: CompletableFuture<R> get() = uncheckedCast(transientValues.resultFuture)

    private fun setLoggingContext() {
        context.pushToLoggingContext()
        MDC.put("flow-id", id.uuid.toString())
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
        //openThreadLocalWormhole()
        setLoggingContext()

        logger.debug { "Calling flow: $logic" }
        val resultOrError = try {

            initialiseFlow()

            Thread.currentThread().contextClassLoader = javaClass.classLoader

            suspend(FlowIORequest.ForceCheckpoint, false)

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
                transientValues.resultFuture.set(resultOrError.value)
                val clientId = transientState.context.clientId
                if (clientId != null) {
                    transientValues.eventsOut += FlowEvent.RPCFlowResult(
                        id,
                        clientId,
                        transientState.subFlows.first().flowClass.name,
                        resultOrError.value,
                        null
                    )
                }
            }
            is Try.Failure -> {
                transientValues.resultFuture.setException(resultOrError.exception)
                val clientId = transientState.context.clientId
                if (clientId != null) {
                    transientValues.eventsOut += FlowEvent.RPCFlowResult(
                        id,
                        clientId,
                        transientState.subFlows.first().flowClass.name,
                        null,
                        resultOrError.exception
                    )
                } else if (transientState.initiatedBy != null) {
                    transientValues.eventsOut += FlowEvent.RemoteFlowError(
                        id,
                        transientState.ourIdentity,
                        transientState.initiatedBy!!.counterparty,
                        transientState.initiatedBy!!.sourceSessionId,
                        transientState.initiatedBy!!.sequenceNo++,
                        resultOrError.exception.message ?: "remote error"
                    )
                }
            }
        }
        transientValues.suspended.set(null)
    }

    private fun sendEvents(
        ioRequest: FlowIORequest<*>
    ) {
        log.info("sendEvents $ioRequest")
        when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                transientValues.eventsOut += FlowEvent.WakeupEvent(id)
            }
            is FlowIORequest.CloseSessions -> TODO()
            is FlowIORequest.ExecuteAsyncOperation -> TODO()
            is FlowIORequest.GetFlowInfo -> TODO()
            is FlowIORequest.Receive -> {
                //nothing to do
                log.info("receive")
            }
            is FlowIORequest.Send -> {
                for (session in ioRequest.sessionToMessage) {
                    val sessionImpl = (session.key as FlowSessionImpl)
                    val sessionId = sessionImpl.sourceSessionId
                    transientValues.eventsOut += FlowEvent.P2PMessage(
                        id,
                        getClosestAncestorInitiatingSubFlow()?.classToInitiateWith?.name ?: logic.javaClass.name,
                        ourIdentity,
                        session.key.counterparty,
                        sessionId,
                        sessionImpl.sequenceNo++,
                        session.value
                    )
                }
                transientValues.eventsOut += FlowEvent.WakeupEvent(id)
            }
            is FlowIORequest.SendAndReceive -> {
                for (session in ioRequest.sessionToMessage) {
                    val sessionImpl = (session.key as FlowSessionImpl)
                    val sessionId = sessionImpl.sourceSessionId
                    transientValues.eventsOut += FlowEvent.P2PMessage(
                        id,
                        getClosestAncestorInitiatingSubFlow()?.classToInitiateWith?.name ?: logic.javaClass.name,
                        ourIdentity,
                        session.key.counterparty,
                        sessionId,
                        sessionImpl.sequenceNo++,
                        session.value
                    )
                }
            }
            is FlowIORequest.Sleep -> TODO()
            is FlowIORequest.WaitForSessionConfirmations -> TODO()
        }
    }

    private fun getClosestAncestorInitiatingSubFlow(): SubFlow.Initiating? {
        for (subFlow in transientState.subFlows.asReversed()) {
            if (subFlow is SubFlow.Initiating) {
                return subFlow
            }
        }
        return null
    }

    private fun processEvent(
        ioRequest: FlowIORequest<*>
    ): Pair<Any?, Boolean> {
        log.info("processEvent $ioRequest")
        return when (ioRequest) {
            FlowIORequest.ForceCheckpoint -> {
                val wakeup = transientState.eventQueue.firstOrNull { it is FlowEvent.WakeupEvent }
                if (wakeup != null) {
                    transientState.eventQueue.remove(wakeup)
                }
                Pair(Unit, (wakeup == null))
            }
            is FlowIORequest.CloseSessions -> TODO()
            is FlowIORequest.ExecuteAsyncOperation -> {
                val wakeup = transientState.eventQueue.firstOrNull { it is FlowEvent.WakeupEvent }
                if (wakeup != null) {
                    transientState.eventQueue.remove(wakeup)
                }
                Pair(Unit, (wakeup == null))
            }
            is FlowIORequest.GetFlowInfo -> TODO()
            is FlowIORequest.Send -> {
                val wakeup = transientState.eventQueue.firstOrNull { it is FlowEvent.WakeupEvent }
                if (wakeup != null) {
                    transientState.eventQueue.remove(wakeup)
                }
                Pair(Unit, (wakeup == null))
            }
            is FlowIORequest.Receive -> {
                val replies =
                    transientState.eventQueue.mapNotNull { FlowEvent.RoutableEvent::class.java.castIfPossible(it) }
                val resultMap = mutableMapOf<FlowSession, SerializedBytes<*>>()
                for (session in ioRequest.sessions) {
                    val sessionId = (session as FlowSessionImpl).sourceSessionId
                    val event = replies.firstOrNull { it.sessionId == sessionId }
                    if (event != null) {
                        if (event is FlowEvent.P2PMessage) {
                            resultMap[session] = event.message
                        } else if (event is FlowEvent.RemoteFlowError) {
                            throw FlowException(event.errorMessage)
                        }
                    }
                }
                val wakeup = (resultMap.size == ioRequest.sessions.size)
                if (wakeup) {
                    for (session in ioRequest.sessions) {
                        val sessionId = (session as FlowSessionImpl).sourceSessionId
                        val event = replies.first { it.sessionId == sessionId }
                        transientState.eventQueue.remove(event)
                    }
                }
                Pair(resultMap, !wakeup)
            }
            is FlowIORequest.SendAndReceive -> {
                val replies =
                    transientState.eventQueue.mapNotNull { FlowEvent.RoutableEvent::class.java.castIfPossible(it) }
                val resultMap = mutableMapOf<FlowSession, SerializedBytes<*>>()
                for (session in ioRequest.sessionToMessage) {
                    val sessionId = (session.key as FlowSessionImpl).sourceSessionId
                    val event = replies.firstOrNull { it.sessionId == sessionId }
                    if (event != null) {
                        if (event is FlowEvent.P2PMessage) {
                            resultMap[session.key] = event.message
                        } else if (event is FlowEvent.RemoteFlowError) {
                            throw FlowException(event.errorMessage)
                        }
                    }
                }
                val wakeup = (resultMap.size == ioRequest.sessionToMessage.size)
                if (wakeup) {
                    for (session in ioRequest.sessionToMessage.keys) {
                        val sessionId = (session as FlowSessionImpl).sourceSessionId
                        val event = replies.first { it.sessionId == sessionId }
                        transientState.eventQueue.remove(event)
                    }
                }
                Pair(resultMap, !wakeup)
            }
            is FlowIORequest.Sleep -> TODO()
            is FlowIORequest.WaitForSessionConfirmations -> TODO()
            else -> throw IllegalArgumentException("unrecognised IOREQUEST type ${ioRequest.javaClass.name}")
        }
    }

    fun waitForCheckpoint(): Checkpoint {
        log.info("waitCheckpoint wait $id")
        val serializedFiber = transientValues.suspended.getOrThrow()
        log.info("waitCheckpoint done $id")
        val stateCopy = FlowState(
            transientState.suspendCount + 1,
            transientState.context,
            transientState.ourIdentity,
            transientState.isKilled,
            transientState.initiatedBy,
            transientState.sessions,
            transientState.subFlows,
            transientState.eventQueue
        )
        return CheckpointImpl(
            id,
            serializedFiber,
            stateCopy
        )
    }

    @Suspendable
    override fun <SUSPENDRETURN : Any> suspend(
        ioRequest: FlowIORequest<SUSPENDRETURN>,
        maySkipCheckpoint: Boolean
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
                transientValues.suspended.set(uncheckedCast(fiberState))
            }
            setLoggingContext()
        }
    }

    @Suspendable
    override fun initiateFlow(destination: Destination, wellKnownParty: Party): FlowSession {
        val sourceSessionId = Trace.SessionId.newInstance()
        log.info("initiateFlow $destination $wellKnownParty $sourceSessionId")
        val newSession = FlowSessionImpl(
            destination,
            wellKnownParty,
            sourceSessionId
        )
        transientState.sessions[newSession.sourceSessionId] = newSession
        return newSession
    }

    @Suspendable
    override fun <SUBFLOWRETURN> subFlow(currentFlow: Flow<*>, subFlow: Flow<SUBFLOWRETURN>): SUBFLOWRETURN {
        val subflowCordapp = transientValues.flowFactory.getCordappForFlow(subFlow)
        val subFlowInfo =
            SubFlow.create(subFlow.javaClass, SubFlowVersion.createSubFlowVersion(subflowCordapp, 6), false)
        transientState.subFlows += subFlowInfo.getOrThrow()
        transientValues.dependencyInjectionService.injectDependencies(subFlow, this)
        suspend(FlowIORequest.ForceCheckpoint, false)
        return try {
            subFlow.call()
        } finally {
            transientState.subFlows.removeLast()
        }
    }

    @Suspendable
    override fun updateTimedFlowTimeout(timeoutSeconds: Long) {
        TODO("Not yet implemented")
    }

}