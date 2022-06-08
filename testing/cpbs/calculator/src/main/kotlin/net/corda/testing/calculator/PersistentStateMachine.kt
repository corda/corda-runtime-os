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
import net.corda.v5.base.util.uncheckedCast
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.CompletableFuture


interface AuthenticatedCommunicator {
    fun send(recipient: String, command: FlowManager.Commands)
    fun myIdentity() : String
}

sealed class WireMessage

sealed class SessionMessage {
    sealed class SystemMessages : SessionMessage() {
        data class SessionEstablished(val channel: FlowManager.SessionChannel) : SystemMessages()
    }
}

data class AuthenticatedMessage(val sender: String, val signature: String = "PRETEND :)", val command: FlowManager.Commands) : WireMessage()

class FlowManager(
    persistenceID : PersistenceId,
    val ctx: ActorContext<WireMessage>,
    val comms: AuthenticatedCommunicator
) : EventSourcedBehavior<WireMessage, FlowManager.Events, FlowManager.State>(persistenceID) {
    sealed class Commands : WireMessage() {

        sealed class SessionCommands : Commands() {

            data class InitiateSession(
                val counterIdentity: String,
                val initiatingProcess: String,
                val responderProcess: String
            ) : SessionCommands()

            data class EstablishSession(val sessionId: String, val processName: String) : SessionCommands()
            data class AckSession(val sessionId: String, val processId: String, val ok: Boolean) : SessionCommands()

            data class SendMessage(val sessionId: String, val msg: SessionMessage) : SessionCommands()
            data class ReceiveMessage(val sessionId: String, val msg: SessionMessage) : SessionCommands()
        }

        sealed class ProcessCommands : Commands() {

            data class RegisterProcess(val processName: String, val creator: (String)->Behavior<SessionMessage>) : ProcessCommands()
            data class SpawnProcess(
                val processName: String,
                val onComplete: CompletableFuture<ActorRef<SessionMessage>>
            ) : ProcessCommands()

            data class FinishProcess(val processId: String) : ProcessCommands()
        }


    }

    override fun commandHandler(): CommandHandler<WireMessage, Events, State> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(AuthenticatedMessage::class.java) { message : AuthenticatedMessage ->
                when (message.command) {
                    is Commands.SessionCommands -> processSessionCommand(message)
                    is Commands.ProcessCommands -> processProcessCommand(message)
                    else -> Effect().none()
                }
            }.build()
    }

    fun processSessionCommand(message: AuthenticatedMessage) : Effect<Events, State> {
        message.command as Commands.SessionCommands

        return when(message.command) {
            is Commands.SessionCommands.InitiateSession -> {
                val sessionId = UUID.randomUUID().toString()
                //Spawn Session
                comms.send(message.command.counterIdentity, Commands.SessionCommands.EstablishSession(sessionId, message.command.responderProcess))

                Effect().persist(Events.AddSession(message.command.initiatingProcess, sessionId, message.command.counterIdentity))
            }
            is Commands.SessionCommands.EstablishSession -> {
                //spawn session
                val processId = UUID.randomUUID().toString()
                Effect()
                    .persist(listOf(
                        Events.SpawnProcess(message.command.processName, processId),
                        Events.AddSession(processId, message.command.sessionId, message.sender),
                        Events.AckSession(message.command.sessionId, processId, true)
                    )).thenRun {
                        //What if we crash after persist before this? HMPF
                        comms.send(message.sender, Commands.SessionCommands.AckSession(message.command.sessionId, processId, true))
                    }
            }
            is Commands.SessionCommands.AckSession -> {
                Effect().persist(Events.AckSession(message.command.sessionId, message.command.processId, true))
            }
            is Commands.SessionCommands.SendMessage -> {
                activeSessions.getOrDefault(message.command.sessionId, CompletableFuture()).thenApply {
                    it.send(message.command.msg)
                }
                Effect().none()
            }
            is Commands.SessionCommands.ReceiveMessage -> {
                activeSessions.getOrDefault(message.command.sessionId, CompletableFuture()).thenApply {
                    it.receive(message.command.msg)
                }
                Effect().none()
            }
        }
    }


    fun processProcessCommand(message: AuthenticatedMessage) : Effect<Events, State> {
        val cmd = message.command as Commands.ProcessCommands
        return when (cmd) {
            is Commands.ProcessCommands.RegisterProcess -> {
                if (message.sender == comms.myIdentity()) {
                    registeredProccesses.getOrDefault(cmd.processName, CompletableFuture()).complete(cmd.creator)
                }

                //handle active processes in state that have not been recognised until now
                Effect().none()
            }
            is Commands.ProcessCommands.SpawnProcess -> {
                if (message.sender != comms.myIdentity()) {
                    Effect().none()
                }
                else {
                    val processId = UUID.randomUUID().toString()
                    //ctx.spawn(creator, "${cmd.processName}-$processId")
                    activeStateMachines.getOrDefault(processId, CompletableFuture()).thenApply {
                        cmd.onComplete.complete(it)
                    }
                    Effect().persist(Events.SpawnProcess(cmd.processName, processId))
                }
            }
            is Commands.ProcessCommands.FinishProcess -> {
                if (message.sender == comms.myIdentity()) {
                    Effect().persist(Events.FinishProcess(cmd.processId))
                } else {
                    Effect().none()
                }
            }
            else -> Effect().none()
        }
    }

    sealed class Events {
        data class SpawnProcess(val processName: String, val processId: String) : Events()
        data class FinishProcess(val processId: String) : Events()

        data class AddSession(val processId: String, val sessionId: String, val counterIdentity: String) : Events()
        data class AckSession(val sessionId: String, val processId: String, val ok: Boolean) : Events()
    }


    override fun eventHandler(): EventHandler<State, Events> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Events.SpawnProcess::class.java) { state, event ->
                registeredProccesses.getOrDefault(event.processName, CompletableFuture()).thenApply {
                    if (state.activeProcesses.contains(event.processId)) {
                        val ref = ctx.spawn(it(event.processId), "${event.processName}-${event.processId}")
                        activeStateMachines.getOrDefault(event.processId, CompletableFuture()).complete(ref)
                    }
                }
                state.apply {
                    activeProcesses.add(event.processId)
                }
            }
            .onEvent(Events.FinishProcess::class.java) { state, event ->
                state.apply {
                    activeProcesses.remove(event.processId)
                    processOutboundSessions.remove(event.processId)?.forEach { sessionId ->
                        sessionToRecipient.remove(sessionId)
                    }
                }
            }
            .onEvent(Events.AddSession::class.java) { state, event ->
                activeStateMachines.getOrDefault(event.processId, CompletableFuture()).thenApply { stateMachine ->
                    activeSessions.getOrDefault(event.sessionId, CompletableFuture()).complete(SessionChannel(
                        event.sessionId,
                        event.counterIdentity,
                        stateMachine,
                        comms
                    ))
                }

                state.apply {
                    sessionToRecipient[event.sessionId] = event.counterIdentity
                    processOutboundSessions.getOrDefault(event.processId, mutableSetOf()).add(event.sessionId)
                }
            }
            .onEvent(Events.AckSession::class.java) { state, event ->
                activeSessions.getOrDefault(event.sessionId, CompletableFuture()).thenApply {
                    it.receive(SessionMessage.SystemMessages.SessionEstablished(it))
                }

                state.apply {

                }
            }
            .build()
    }

    data class SessionChannel(val sessionId: String, val recipient: String, val ref: ActorRef<SessionMessage>, val comms: AuthenticatedCommunicator, var open: Boolean = false) {
        fun send(msg: SessionMessage) {
            comms.send(recipient, Commands.SessionCommands.ReceiveMessage(sessionId, msg))
        }

        fun receive(msg: SessionMessage) {
            ref.tell(msg)
        }
    }

    val registeredProccesses = mutableMapOf<String, CompletableFuture<(String)->Behavior<SessionMessage>>>()
    val activeStateMachines = mutableMapOf<String, CompletableFuture<ActorRef<SessionMessage>>>()
    val activeSessions = mutableMapOf<String, CompletableFuture<SessionChannel>>()

    data class State(
        val isReady: Boolean = false,
        val activeProcesses: MutableCollection<String> = mutableSetOf(),//ProcessID to ProcessIDs
        val sessionToRecipient: MutableMap<String, String> = mutableMapOf(), //to recipient
        val processOutboundSessions: MutableMap<String, MutableCollection<String>> = mutableMapOf(),
        val outboundSessionToProcessId: MutableMap<String, String> = mutableMapOf()
    ) {
        companion object {
            val EMPTY = State()
        }
    }

    override fun emptyState(): State {
        return State.EMPTY
    }

}

class PersistentStateMachine(
    persistenceID : PersistenceId,
    val flowManagerRef: ActorRef<WireMessage>,
    val context: ActorContext<SessionMessage>,
    val executionContext : Context
)
    : EventSourcedBehavior<SessionMessage, PersistentStateMachine.Events, PersistentStateMachine.State> (persistenceID)
{

    companion object {
        fun create(id: String, ref: ActorRef<WireMessage>, ctx : Context) : Behavior<SessionMessage> {
            return Behaviors.setup {
                PersistentStateMachine(PersistenceId.ofUniqueId(id), ref, it, ctx)
            }
        }
    }

    sealed class Events {
        object Start : Events()
        data class Msg(val msg: SessionMessage) : Events()
    }

    abstract class Context {
        data class ComputationPathContext(
            val processingStages : MutableList<(Any?)->StateMachineEvent> = mutableListOf(),
            val id: String = UUID.randomUUID().toString()
        )

        val initiatingStages = ComputationPathContext()
        val respondingStages = mutableMapOf<Class<*>, ComputationPathContext>()

        var computedResult: Any? = null

        var currentContextBuilder: ComputationPathContext? = null

        sealed class StateMachineEvent {
            data class WaitFor(val event: Class<*>, val from: Any?) : StateMachineEvent()
            data class WaitAll(val list: List<WaitFor>) : StateMachineEvent()
            data class ProceedNow(val eventForNextStep: Any) : StateMachineEvent()
            internal data class Finish(val result: Any?) : StateMachineEvent()

            data class RevertTo(val step: Int, val eventToPass: Any? = null) : StateMachineEvent()
            data class Cancel(val signal: Class<*>? = null) : StateMachineEvent() //Event does not match this execution context

            internal object Yield: StateMachineEvent() //Event is good but need more
            object Fresh: StateMachineEvent() //Just started
        }


        fun<T: Any> onEvent(clazz: Class<*>, operation: Context.(T)->StateMachineEvent) = this.let { flowContext ->
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
                     flowContext.computedResult = result
                     StateMachineEvent.Finish(result)
                 }

             }
             currentContextBuilder = null
             flowContext
        }

        data class RunningContext(var stage : Int = 0, val context: ComputationPathContext, var state: StateMachineEvent = StateMachineEvent.Fresh)

        lateinit var runContext : RunningContext

        fun start(event: Any? = null) : StateMachineEvent {
            val contextToProgress = event?.let { respondingStages[event.javaClass]!! } ?: initiatingStages
            runContext = RunningContext(context = contextToProgress)
            return process(event)
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

        fun process(event: Any? = null, from: Any? = null) : StateMachineEvent {
            when (matchArgs(runContext.state, event, from)) {
                MatchResult.WAIT_FOR_MORE -> return StateMachineEvent.Yield
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

    override fun eventHandler(): EventHandler<State, Events> {
        return newEventHandlerBuilder()
            .forAnyState()
        /*    .onEvent(Events.ContextUpdate::class.java) { state, event ->
                state.context.process(this).stageFuture.thenApply {
                    context.self.tell(Commands.ContextFutureResolved(it))
                }
                state
            } */
            .build()
    }


    fun establishSession(recipient: String, responderProcess: String) {
        flowManagerRef.tell(FlowManager.Commands.SessionCommands.InitiateSession(recipient, persistenceId().id(), responderProcess))
    }

    data class State(val context: Context)

    override fun emptyState(): State {
        return State(executionContext)
    }

    sealed class Commands : SessionMessage() {
        data class ContextFutureResolved(val res: Any) : Commands()
    }

    override fun commandHandler(): CommandHandler<SessionMessage, Events, State> {
        return newCommandHandlerBuilder()
            .forAnyState()
           /* .onCommand(Commands.ContextFutureResolved::class.java) { msg ->
                Effect().persist(Events.ContextUpdate())
            } */
            .onCommand(SessionMessage::class.java) { msg ->
                Effect().none()
            }.build()
    }
}