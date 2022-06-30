package net.corda.testing.calculator.system

import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior

class ReliablePersistentSession(id: String) : EventSourcedBehavior<ReliablePersistentSession.Commands, ReliablePersistentSession.Events, ReliablePersistentSession.State>(PersistenceId.ofUniqueId(id)) {

    sealed class Commands {

    }

    sealed class Events {

    }

    data class State(
        val ok : Boolean
    ) {}

    override fun emptyState(): State {
        TODO("Not yet implemented")
    }

    override fun commandHandler(): CommandHandler<Commands, Events, State> {
        TODO("Not yet implemented")
    }

    override fun eventHandler(): EventHandler<State, Events> {
        TODO("Not yet implemented")
    }

}

/*
   Transaction:
   1. Receive params
   2. Establish connection to party
   3. Party checks something
   4. Receive response
   5. Determine


   class CollectSignaturesContext {
        fun init(targets: List<Party>) {
            spawnSession(target,
        }

        fun onSignature(signature: Signature)
   }


    FlowBuilder()
        .name("MPC")
        .forVersion("0.1")
        .onMessage(InitFlowMessage.ID())
        .setupStatefulHandler(FlowInitialContext())
 */