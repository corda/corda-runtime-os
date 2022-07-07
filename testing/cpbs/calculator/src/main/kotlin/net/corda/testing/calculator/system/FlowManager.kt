package net.corda.testing.calculator.system

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import net.corda.testing.calculator.AuthenticatedCommunicator
import net.corda.testing.calculator.AuthenticatedMessage
import net.corda.testing.calculator.SessionMessage
import java.util.*
import java.util.concurrent.CompletableFuture

open class WireMessage

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

            data class RegisterProcess(
                val processName: String,
                val creator: (String)-> Behavior<SessionMessage>
            ) : ProcessCommands()
            data class SpawnProcess(
                val processName: String,
                val onComplete: CompletableFuture<ActorRef<SessionMessage>>
            ) : ProcessCommands()

            data class FinishProcess(val processId: String, val result: Any? = null) : ProcessCommands()
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

    val registeredProccesses = mutableMapOf<String, CompletableFuture<(String) -> Behavior<SessionMessage>>>()
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