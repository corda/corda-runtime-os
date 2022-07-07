package net.corda.testing.calculator

import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.AskPattern
import akka.pattern.Patterns
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import net.corda.testing.calculator.system.BidirectionalSession
import net.corda.testing.calculator.system.SessionMessageID
import java.util.*
import java.util.concurrent.CompletableFuture
/*

class BackchainResolutionFlow(
    val id: String,
    val remote: ActorRef<BidirectionalSession.Commands>,
    val context: ActorContext<SessionMessage>
) : EventSourcedBehavior<SessionMessage, BackchainResolutionFlow.Events, BackchainResolutionFlow.State>(
    PersistenceId.ofUniqueId(id)) {

    sealed class Commands : SessionMessage() {
        data class Request(val id: String) : Commands()
        data class Response(val txs: List<Transaction>) : Commands()
    }

    sealed class Events {
        data class RecordTransaction(val tx: Transaction)
    }

    data class Transaction(val id: String, val inputs: List<String>?) {
        fun verify() = true
    }
    data class State(
        val unresolved: MutableSet<Transaction>,
        val processed: MutableList<Transaction>
    )

    override fun emptyState(): State {
        return State(mutableSetOf(), mutableListOf())
    }

    override fun commandHandler(): CommandHandler<SessionMessage, Events, State> {
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(Commands.Request::class.java) { state, cmd ->
                Effect()
                    .none()
                    .thenRun {
                        val future = CompletableFuture<Unit>()
                        remote.tell(
                            BidirectionalSession.Commands.SendMessage(
                                BidirectionalSession.QueuedMessage(
                                    SessionMessageID(UUID.randomUUID().toString()),
                                    Commands.Response(emptyList())
                                ),
                                future
                            )
                        )
                }
            }.build()
    }

    override fun eventHandler(): EventHandler<State, Events> {
        TODO("Not yet implemented")
    }
}*/