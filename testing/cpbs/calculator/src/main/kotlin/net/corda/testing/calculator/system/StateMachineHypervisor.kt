package net.corda.testing.calculator.system

import akka.actor.typed.ActorRef
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.RecoveryCompleted
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import akka.persistence.typed.javadsl.SignalHandler
import net.corda.testing.calculator.SessionMessage
import net.corda.v5.base.util.uncheckedCast

sealed class HypervisorCommands: SessionMessage() {
    data class Initialize(val contextOverride: Any? = null) : HypervisorCommands()
    data class DeliverMessage(val msg: SessionMessage) : HypervisorCommands()
    data class Ack(val msg: SessionMessage) : HypervisorCommands()
}

sealed class HypervisorEvents {
    sealed class Empty : HypervisorEvents() {
        data class InitializeStateMachine(val context: Any) : Empty()
    }

    sealed class Running : HypervisorEvents() {
        data class UpdateContext(val updated: Any) : Running()
        data class SendMessage(val target: String, val msg : SessionMessage) : Running()
        data class ReceiveAck(val msgId: String) : Running()
        data class ReceiveMessage(val from: String, val msg: SessionMessage) : Running()

        data class SpawnActor(val actorId: String, val processName: String, val initialContext: Any) : Running()
        data class FinishActor(val actorId: String, val result: Any) : Running()
    }
}


class StateMachineHypervisor<ContextType: Any, StateMachine: ExplicitStateMachineV2<ContextType>>(
    id: String,
    val stateMachineCreator : (ContextType, StateMachinePersistence<ContextType>)->StateMachine,
    val emptyStateMachineContext : ()->ContextType,
    val sendMsg: (String, SessionMessage)->Unit
) : EventSourcedBehavior<SessionMessage, HypervisorEvents, StateMachineHypervisor.HypervisorState>(PersistenceId.ofUniqueId(id)) {


    data class ForkInstanceInfo(val processName: String, val initialContext: Any)

    sealed class HypervisorState {
        data class Initialized(
            var stateMachineContext: Any,
            val receivedMessages: MutableSet<SessionMessageID>,
            val forks: MutableMap<String, ForkInstanceInfo>,
            val pendingMessage: MutableSet<SessionMessage>
        ) : HypervisorState()

        object EMPTY : HypervisorState()

        fun forceInitialized() : Initialized {
            return uncheckedCast(this)
        }
    }

    fun spawnActor(actorId: String, processName: String, initialContext: Any) {

    }

    var stateMachineExecutable: StateMachine? = null

    override fun signalHandler() =
        newSignalHandlerBuilder()
            .onSignal(RecoveryCompleted.instance()) {
                if (it is HypervisorState.EMPTY)
                    return@onSignal

                it.forceInitialized().forks.forEach { (key, info) ->
                    spawnActor(key, info.processName, info.initialContext)
                }
                stateMachineExecutable = stateMachineExecutable
                    ?: stateMachineCreator(uncheckedCast(it.forceInitialized().stateMachineContext), persistence)
            }
            .build()

    override fun eventHandler() =
        newEventHandlerBuilder()
            .forAnyState()
            .onEvent(HypervisorEvents.Running.ReceiveMessage::class.java) { state, event ->
                state.forceInitialized().apply {
                    receivedMessages.add(SessionMessageID(event.msg.id))
                }
            }
            .onEvent(HypervisorEvents.Empty.InitializeStateMachine::class.java) { state, event ->
                HypervisorState.Initialized(event.context, mutableSetOf(), mutableMapOf(), mutableSetOf())
            }
            .onEvent(HypervisorEvents.Running.UpdateContext::class.java) { state, event ->
                state.forceInitialized().apply {
                    stateMachineContext = event.updated
                }
            }.onEvent(HypervisorEvents.Running.SpawnActor::class.java) { state, event ->
                state.forceInitialized().apply {
                    forks[event.actorId] = ForkInstanceInfo(event.processName, event.initialContext)
                }
            }.onEvent(HypervisorEvents.Running.ReceiveAck::class.java) { state, event ->
                state.forceInitialized().apply {
                    pendingMessage.findLast { it.id == event.msgId }?.let {
                        pendingMessage.remove(it)
                    }
                }
            }.onEvent(HypervisorEvents.Running.SendMessage::class.java) { state, event ->
                state.forceInitialized().apply {
                    pendingMessage.add(event.msg)
                }
            }.onEvent(HypervisorEvents.Running.FinishActor::class.java) { state, event ->
                state.forceInitialized().apply {
                    forks.remove(event.actorId)
                }
            }
            .build()

    class EventSourcedPersistence<Type> : StateMachinePersistence<Type> {

        val queue = mutableListOf<HypervisorEvents.Running>()
        override fun persistState(state: Type) {
            queue.add(HypervisorEvents.Running.UpdateContext(uncheckedCast(state)))
        }

        override fun queueOutboundMessage(target: String, msg: SessionMessage) {
            queue.add(HypervisorEvents.Running.SendMessage(target, msg))
        }

    }
    val persistence = EventSourcedPersistence<ContextType>()

    override fun commandHandler() =
        newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(HypervisorCommands.Initialize::class.java) { state, cmd ->
                println("No Crash ... yet")
                val initialContext = cmd.contextOverride ?: emptyStateMachineContext()
                stateMachineExecutable = stateMachineExecutable ?: stateMachineCreator(uncheckedCast(initialContext), persistence)
                stateMachineExecutable?.initialize()

                val events = listOf(HypervisorEvents.Empty.InitializeStateMachine(initialContext)).plus(persistence.queue.toList())
                Effect()
                    .persist(events)
                    .thenRun {
                        events.processMsgs()
                    }
            }
            .onCommand(HypervisorCommands.DeliverMessage::class.java) { cmd ->
                processSessionMessage(cmd.msg)
            }
            .onCommand(SessionMessage::class.java) { cmd ->
                processSessionMessage(cmd)
            }.onCommand(HypervisorCommands.Ack::class.java) { cmd ->
                Effect().persist(HypervisorEvents.Running.ReceiveAck(cmd.id))
            }
            .build()

    fun List<HypervisorEvents>.processMsgs() {
        this.filter {
            it is HypervisorEvents.Running.SendMessage
        }.forEach {
            it as HypervisorEvents.Running.SendMessage
            sendMsg(it.target, it.msg)
        }
    }

    fun processSessionMessage(msg: SessionMessage) = Effect().let { effect->
        persistence.queue.clear()
        val result = stateMachineExecutable!!.receive(msg)
        val resultingEvents = persistence.queue.toList()

        val toPersist = resultingEvents.plus(HypervisorEvents.Running.ReceiveMessage(msg.sender(), msg))

        return@let if (result == ExplicitStateMachineV2.ConsumeResult.CONSUMED) {
            effect
                .persist(toPersist)
                .thenRun {
                    //Ack message
                    sendMsg(msg.sender(), HypervisorCommands.Ack(msg))
                    toPersist.processMsgs()
                }
        } else {
            effect
                .none()
        }
    }

    override fun emptyState() = HypervisorState.EMPTY
}