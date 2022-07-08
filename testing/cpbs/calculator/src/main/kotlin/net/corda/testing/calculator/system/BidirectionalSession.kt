package net.corda.testing.calculator.system

import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.testing.calculator.SessionMessage
import java.util.concurrent.CompletableFuture

data class SessionMessageID(@JsonProperty("msg") val id: String)

sealed class LocalPipe {
    data class Deliver(val msg: BidirectionalSession.QueuedMessage) : LocalPipe()
    data class Acknowledge(val id: SessionMessageID) : LocalPipe()
}

class BidirectionalSession(
    id: String,
    val tellLocalActor: (LocalPipe) -> Unit,
    val tellRemoteActor: (Commands) -> Unit
) : EventSourcedBehavior<BidirectionalSession.Commands, BidirectionalSession.Events, BidirectionalSession.State>(
    PersistenceId.ofUniqueId(id)
) {

    sealed class Commands : SessionMessage() {
        data class DeliverMessage(@JsonProperty("msg") val msg: QueuedMessage) : Commands()
        data class SendMessage(@JsonProperty("msg") val msg: QueuedMessage, val doneSignal: CompletableFuture<Unit>?) :  Commands()

        sealed class Reconcile(val ids: List<SessionMessageID>) : Commands() {
            class Request(@JsonProperty("ids") ids: List<SessionMessageID>, @JsonProperty("isLocal") val isLocal: Boolean) : Reconcile(ids)
            //f-in serialization ..
            class Response(@JsonProperty("missingIDs") missingIDs: List<SessionMessageID>, @JsonProperty("previouslyAcknowledged") val previouslyAcknowledged: List<Acknowledge>, @JsonProperty("queueDiff") val queueDiff: List<SessionMessageID>) : Reconcile(missingIDs)
            class Pull(@JsonProperty("ids") ids: List<SessionMessageID>) : Reconcile(ids)
        }

        data class Acknowledge(@JsonProperty("id") val sessionMessageID: SessionMessageID) :  Commands()
    }

    sealed class Events {
        data class RecordInbound(@JsonProperty("msg") val msg: QueuedMessage) : Events()
        data class RecordOutbound(@JsonProperty("msg") val msg: QueuedMessage) : Events()
        data class Acknowledge(@JsonProperty("id") val id: SessionMessageID) : Events()
    }

    data class QueuedMessage(var id: SessionMessageID, var payload: SessionMessage)

    data class State(
        @JsonProperty("inbound")            val inbound: MutableList<QueuedMessage>,
        @JsonProperty("outbound")           val outbound: MutableList<QueuedMessage>,
        @JsonProperty("queuedIDs")          val queuedIDs: MutableMap<SessionMessageID, MutableList<QueuedMessage>>,
        @JsonProperty("acknowledgedIDs")    val acknowledgedIDs: MutableSet<SessionMessageID>
    )

    override fun emptyState() = State(inbound = mutableListOf(), outbound = mutableListOf(), queuedIDs = mutableMapOf(), acknowledgedIDs = mutableSetOf())

    override fun commandHandler(): CommandHandler<Commands, Events, State> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Commands.DeliverMessage::class.java) { cmd : Commands.DeliverMessage ->
                Effect()
                    .persist(Events.RecordInbound(cmd.msg))
                    .thenRun {
                        println("[${persistenceId().id()}] Sending remote ack - ${cmd.msg.id}")
                        tellRemoteActor(Commands.Acknowledge(cmd.msg.id))
                    }
                    .thenRun {
                        tellLocalActor(LocalPipe.Deliver(cmd.msg))
                    }
            }.onCommand(Commands.SendMessage::class.java) { cmd : Commands.SendMessage ->
                Effect()
                    .persist(Events.RecordOutbound(cmd.msg))
                    .thenRun {
                        tellLocalActor(LocalPipe.Acknowledge(cmd.msg.id))
                    }
                    .thenRun {
                        tellRemoteActor(Commands.DeliverMessage(cmd.msg))
                    }
            }.onCommand(Commands.Acknowledge::class.java) { cmd : Commands.Acknowledge ->
                Effect()
                    .persist(Events.Acknowledge(cmd.sessionMessageID))
            }.onCommand(Commands.Reconcile.Pull::class.java) { state, cmd: Commands.Reconcile.Pull ->
                Effect()
                    .none()
                    .thenRun {
                        cmd.ids.forEach { id ->
                            state.queuedIDs[id]?.let {
                                tellRemoteActor(Commands.DeliverMessage(it.findLast { queued -> queued.id == id }!!))
                            }
                        }
                    }
            }.
            onCommand(Commands.Reconcile.Request::class.java) { state, cmd : Commands.Reconcile.Request ->
                Effect()
                    .none()
                    .thenRun {
                        val alreadyKnown = cmd.ids.toSet()
                        val targetQueue = if (cmd.isLocal) state.inbound else state.outbound
                        val queueDiff = targetQueue.filter {
                            alreadyKnown.contains(it.id)
                        }

                        val acknowledgedIDs = mutableListOf<SessionMessageID>()
                        val missingIDs = cmd.ids.filter {
                            val isNotPersistedPreviously = !state.queuedIDs.contains(it)
                            val isNotAcknowledged = !state.acknowledgedIDs.contains(it)
                            if (!isNotAcknowledged) {
                                //Message is acknowledged, but remote is not aware.
                                acknowledgedIDs.add(it)
                            }

                            isNotPersistedPreviously && isNotAcknowledged
                        }

                        tellRemoteActor(Commands.Reconcile.Response(missingIDs, acknowledgedIDs.map {
                            Commands.Acknowledge(it)
                        }, queueDiff.map { it.id }))
                    }
            }.onCommand(Commands.Reconcile.Response::class.java) { state, cmd: Commands.Reconcile.Response ->
                val effect = when (cmd.previouslyAcknowledged.isEmpty()) {
                    true -> Effect().none()
                    false -> Effect().persist(cmd.previouslyAcknowledged.map { Events.Acknowledge(it.sessionMessageID) })
                }

                effect.thenRun {
                    val missing = cmd.ids.toSet()
                    state.outbound.filter {
                        missing.contains(it.id)
                    }.forEach {
                        tellRemoteActor(Commands.DeliverMessage(it))
                    }
                }
            }.
            build()
    }

    override fun eventHandler(): EventHandler<State, Events> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Events.RecordInbound::class.java) { state, event ->
                state.apply {
                    println("[${persistenceId().id()}]Inbound: ${event.msg.id}")
                    if (!queuedIDs.containsKey(event.msg.id)) {
                        queuedIDs.put(event.msg.id, inbound)
                        inbound.add(event.msg)
                    }

                }
            }.onEvent(Events.RecordOutbound::class.java) { state, event ->
                state.apply {
                    println("[${persistenceId().id()}]Outbound: ${event.msg.id}")
                    if (!queuedIDs.containsKey(event.msg.id)) {
                        queuedIDs.put(event.msg.id, outbound)
                        outbound.add(event.msg)
                    }
                }
            }.onEvent(Events.Acknowledge::class.java) { state, event ->
                state.apply {
                    println("[${persistenceId().id()}]Ack: ${event.id}")
                    queuedIDs[event.id]?.let {
                        it.removeIf { msg ->
                            msg.id == event.id
                        }
                    } // ?: throw???
                    queuedIDs.remove(event.id)
                    acknowledgedIDs.add(event.id)
                }
            }
            .build()
    }

}