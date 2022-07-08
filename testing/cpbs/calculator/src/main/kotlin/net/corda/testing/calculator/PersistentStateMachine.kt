package net.corda.testing.calculator

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import jdk.jfr.Event
import net.corda.testing.calculator.system.FlowManager
import net.corda.testing.calculator.system.WireMessage
import net.corda.v5.base.util.uncheckedCast
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.CompletableFuture


interface AuthenticatedCommunicator {
    fun send(recipient: String, command: FlowManager.Commands)
    fun myIdentity() : String
    fun authenticate(command: FlowManager.Commands) : AuthenticatedMessage
}


open class SessionMessage {
    internal var sessionId : String = "undefined"
    fun whichSession() = sessionId

    internal var authenticatedSender: String = "unknown"
    fun sender() = authenticatedSender

    internal var id: String = UUID.randomUUID().toString()

    sealed class SystemMessages : SessionMessage() {
        data class SessionEstablished(val channel: FlowManager.SessionChannel) : SystemMessages()
    }
}

data class AuthenticatedMessage(val sender: String, val signature: String = "PRETEND :)", val command: FlowManager.Commands) : WireMessage()


typealias ProcessID = String

sealed class ChannelIdentity {
    data class ExternalTarget(val identifier: String, val externalProcessURL: String /*Actor URL, tcp, responding flow*/) : ChannelIdentity()
    data class InternalTarget(val processURL: String) : ChannelIdentity()
}

class PersistentStateMachine(
    val persistenceID : PersistenceId,
    val flowManagerRef: ActorRef<WireMessage>,
    val executionContext : Context,
    val comms: AuthenticatedCommunicator,
    val context: ActorContext<SessionMessage>,
)
    : EventSourcedBehavior<SessionMessage, PersistentStateMachine.Events, PersistentStateMachine.State> (persistenceID)
{

    companion object {
        fun create(id: String, ref: ActorRef<WireMessage>, comms: AuthenticatedCommunicator, ctx : Context) : Behavior<SessionMessage> {
            return Behaviors.setup { actorContext ->
                PersistentStateMachine(PersistenceId.ofUniqueId(id), ref, ctx, comms, actorContext )
            }
        }
    }

    fun injectContext() {
        executionContext.apply {
            /* .then { msg -> //this is step 3

                    waitFor -> MessageFromAnotherParty
                }.fork("id1") { partyMsg ->
                    start().then().finally() : ComputedResultInFork1 <- joining will give result from finally
                }.fork("id2") { partyMsg ->

                }.then { partyMsg->

                }.join("id1").then { computedResult: ComputedResultInFork1 ->

                }
             */
            fork = {
                //tell flow dashboard to spawn identical process, and give it a copy of executionContext at current step.
                "${UUID.randomUUID()}"
            }
            join = { pid ->
                //
                Context.StateMachineEvent.WaitFor(Unit::class.java, null)
            }

            establishPersistentSession = {
                if (it is ChannelIdentity.ExternalTarget) {
                    flowManagerRef.tell(
                        comms.authenticate(
                            FlowManager.Commands.SessionCommands.InitiateSession(
                                it.identifier,
                                persistenceID.id(),
                                it.externalProcessURL,
                            )
                        )
                    )
                    Context.StateMachineEvent.WaitFor(
                        SessionMessage.SystemMessages.SessionEstablished::class.java,
                        flowManagerRef
                    )
                } else {
                    //Implement actor sessions later if they are needed; Presumably they are so that 2 phase commit can be hidden
                    Context.StateMachineEvent.WaitFor(
                        SessionMessage.SystemMessages.SessionEstablished::class.java,
                        flowManagerRef
                    )
                }
            }
        }
    }

    abstract class Context {
        data class ComputationPathContext(
            val processingStages : MutableList<(Any?)->StateMachineEvent> = mutableListOf(),
            val id: String = UUID.randomUUID().toString()
        )

        lateinit var establishPersistentSession : (ChannelIdentity) -> StateMachineEvent.WaitFor
        lateinit var fork: () -> ProcessID
        lateinit var join: (ProcessID) -> StateMachineEvent.WaitFor


        val initiatingStages = ComputationPathContext()
        val respondingStages = mutableMapOf<Class<*>, ComputationPathContext>()

        var currentContextBuilder: ComputationPathContext? = null

        sealed class StateMachineEvent {
            data class WaitFor(val event: Class<*>, val from: Any?) : StateMachineEvent()
            data class WaitAll(val list: List<WaitFor>) : StateMachineEvent()
            data class ProceedNow(val eventForNextStep: Any) : StateMachineEvent()
            internal data class Finish(val result: Any?) : StateMachineEvent()

            data class RevertTo(val step: Int, val eventToPass: Any? = null) : StateMachineEvent()
            data class Cancel(val signal: Class<*>? = null) : StateMachineEvent() //Event does not match this execution context

            internal object EventBuffered: StateMachineEvent() //Event is good but need more
            object Fresh: StateMachineEvent() //Just started
        }

        data class EventWrapper<T>(val event: T, val sender: ChannelIdentity)

        fun<T: Any> onEvent(clazz: Class<*>, operation: Context.(EventWrapper<T>)->StateMachineEvent) = this.let { flowContext ->
            if (currentContextBuilder != null) throw IllegalStateException("Trying to build something weird")

            respondingStages[clazz] = ComputationPathContext()
            currentContextBuilder = respondingStages[clazz]
            currentContextBuilder!!.apply {
                processingStages.add { event ->
                    flowContext.operation(uncheckedCast(event))
                }
            }
            flowContext
        }

         fun start(operation: Context.()->StateMachineEvent) = this.let { flowContext ->
            currentContextBuilder = initiatingStages
            currentContextBuilder!!.apply {
                processingStages.add { unused ->
                    flowContext.operation()
                }
            }
             flowContext
        }

         fun<T> then(operation: Context.(T)->StateMachineEvent) = this.let { flowContext ->
             currentContextBuilder!!.apply {
                 processingStages.add { event ->
                     flowContext.operation(uncheckedCast(event))
                 }
             }
             flowContext
        }

         fun<T, R> finally(operation: Context.(T)->R)  = this.let { flowContext ->
             currentContextBuilder!!.apply {
                 processingStages.add { event ->
                     val result = flowContext.operation(uncheckedCast(event))
                     StateMachineEvent.Finish(result)
                 }

             }
             currentContextBuilder = null
             flowContext
        }

        data class RunningContext(var stage : Int = 0, val context: ComputationPathContext, var state: StateMachineEvent = StateMachineEvent.Fresh)

        lateinit var runContext : RunningContext

        private fun selectContextToRun(event: Any? = null) : RunningContext {
            val contextToProgress = event?.let { respondingStages[event.javaClass]!! } ?: initiatingStages
            return RunningContext(context = contextToProgress)
        }

        var isStarted : Boolean = false

        fun start(id: String, event: Any? = null) : StateMachineEvent {
            runContext = selectContextToRun(event)
            isStarted = true
            return process(id, event)
        }

        val bufferedEvents = mutableMapOf<Int, Pair<Any?, Any?>>()

        enum class MatchResult {
            PASS,
            FAIL,
            WAIT_FOR_MORE
        }
        private fun matchArgs(state: StateMachineEvent, event: Any? = null, from: Any? = null) : MatchResult {
            when (state) {
                is StateMachineEvent.WaitFor -> {
                    val isNotSameClass = event?.javaClass != state.event
                    val isNotFromRightSender = state.from != from

                    if (isNotSameClass || isNotFromRightSender) return MatchResult.FAIL
                }
                is StateMachineEvent.WaitAll -> {
                    var matched = false
                    var failed = false
                    state.list.forEachIndexed { index, item ->
                        if (bufferedEvents.containsKey(index)) return@forEachIndexed

                        if (matchArgs(item, event, from) == MatchResult.PASS) {
                            bufferedEvents[index] = Pair(event, from)
                            matched = true
                        } else {
                            failed = true
                        }
                    }

                    if (!matched) {
                        return MatchResult.FAIL
                    }
                    if (failed) { //If we didnt match anything do nothing; if we reach here we have made a match, but failed the whole comparison
                        return MatchResult.WAIT_FOR_MORE
                    }
                }
            }
            return MatchResult.PASS
        }

        data class MultipleEvents(val events: List<Pair<Any?, Any?>>)

        val processedEvents = mutableSetOf<String>()

        fun synchronize(persistedEvent: Events.EventProcessed) {
            if (processedEvents.contains(persistedEvent.id))
                return

            processedEvents.add(persistedEvent.id)

            if (!isStarted)
                runContext = selectContextToRun(persistedEvent.eventValue)

            val stateMachineEvent = persistedEvent.result
            when (stateMachineEvent) {
                StateMachineEvent.Fresh -> {

                }
                StateMachineEvent.EventBuffered -> {
                    matchArgs(stateMachineEvent, persistedEvent.eventValue, persistedEvent.eventSender)
                }
                is StateMachineEvent.Cancel -> {

                }
                is StateMachineEvent.Finish -> {
                    runContext.stage++
                    bufferedEvents.clear()
                }
                is StateMachineEvent.ProceedNow -> {
                    runContext.stage++
                    bufferedEvents.clear()
                }
                is StateMachineEvent.RevertTo -> {
                    runContext.stage = stateMachineEvent.step
                }
                is StateMachineEvent.WaitAll -> {
                    runContext.stage++
                    bufferedEvents.clear()
                }
                is StateMachineEvent.WaitFor -> {
                    runContext.stage++
                    bufferedEvents.clear()
                }
            }
        }

        fun process(id: String, event: Any? = null, from: Any? = null) : StateMachineEvent {
            processedEvents.add(id)
            when (matchArgs(runContext.state, event, from)) {
                MatchResult.WAIT_FOR_MORE -> return StateMachineEvent.EventBuffered
                MatchResult.FAIL -> return StateMachineEvent.Cancel()
                else -> {
                    //Why is kotlin complaining about no else wtf. .-.
                }
            }

            val eventToProcess = runContext.state.let {
                if (it is StateMachineEvent.WaitAll) {
                    val orderedEvents = it.list.mapIndexed { index, item ->
                        bufferedEvents[index] ?: throw IllegalStateException("Arguments matched, but nothing buffered for index: ${index};")
                    }

                    MultipleEvents(orderedEvents)
                } else {
                    event
                }
            }

            val stage = runContext.context.processingStages[runContext.stage]
            val resultingEvent = stage.invoke(eventToProcess)
            when (resultingEvent) {
                is StateMachineEvent.Cancel -> return resultingEvent
                is StateMachineEvent.RevertTo -> {
                    runContext.stage = resultingEvent.step
                    return resultingEvent
                }
                else -> {
                    runContext.state = resultingEvent
                }
            }
            runContext.stage++
            bufferedEvents.clear()

            return resultingEvent
        }
    }

    data class State(val context: Context)

    override fun emptyState(): State {
        return State(executionContext)
    }


    sealed class Events {
        data class EventProcessed(
            val eventValue: Any?,
            val eventSender: Any?,
            val result: Context.StateMachineEvent,
            val id: String
        ) : Events()
    }

    sealed class Commands : SessionMessage() {
        data class DeliverMessage(val event: Any, val from: Any? = null) : Commands()
        data class DeliverSignal(val signal: Any) : Commands()
        data class DeliverForkPayload(val payload: Any) : Commands()
        data class Start(val event: Any? = null) : Commands()
    }

    override fun eventHandler(): EventHandler<State, Events> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Events.EventProcessed::class.java) { state, event ->
                executionContext.apply { synchronize(event) }

                state.apply {

                }
            }
            .build()
    }

    override fun commandHandler(): CommandHandler<SessionMessage, Events, State> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Commands.Start::class.java) { cmd ->
                val id = UUID.randomUUID().toString()
                val result = executionContext.start(id, cmd.event)
                val event = Events.EventProcessed(cmd.event, null, result, id)

                Effect()
                    .persist(event).thenRun {
                        if (result is Context.StateMachineEvent.ProceedNow) {
                            context.self.tell(Commands.DeliverMessage(result.eventForNextStep))
                        } else if (result is Context.StateMachineEvent.Finish) {
                            flowManagerRef.tell(comms.authenticate(FlowManager.Commands.ProcessCommands.FinishProcess(persistenceID.id(), result.result)))
                        }
                    }
            }.onCommand(Commands.DeliverMessage::class.java) { cmd ->
                val id = UUID.randomUUID().toString()
                val result = executionContext.start(id, cmd.event)
                val event = Events.EventProcessed(cmd.event, cmd.from, result, id)

                Effect()
                    .persist(event).thenRun {
                        if (result is Context.StateMachineEvent.ProceedNow) {
                            context.self.tell(Commands.DeliverMessage(result.eventForNextStep))
                        } else if (result is Context.StateMachineEvent.Finish) {
                            flowManagerRef.tell(comms.authenticate(FlowManager.Commands.ProcessCommands.FinishProcess(persistenceID.id(), result.result)))
                        }
                    }
            }.onCommand(Commands.DeliverSignal::class.java) { cmd ->
                val id = UUID.randomUUID().toString()
                val result = executionContext.start(id, cmd.signal)
                val event = Events.EventProcessed(cmd.signal, null, result, id)

                Effect()
                    .persist(event).thenRun {
                        if (result is Context.StateMachineEvent.ProceedNow) {
                            context.self.tell(Commands.DeliverMessage(result.eventForNextStep))
                        } else if (result is Context.StateMachineEvent.Finish) {
                            flowManagerRef.tell(comms.authenticate(FlowManager.Commands.ProcessCommands.FinishProcess(persistenceID.id(), result.result)))
                        }
                    }
            }
            .build()
    }
}