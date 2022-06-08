package net.corda.testing.akka

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.*
import akka.pattern.Patterns
import akka.persistence.AbstractPersistentActor
import akka.persistence.SnapshotOffer
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.v5.base.util.uncheckedCast
import java.io.Serializable
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


sealed class SessionMessages

sealed class FlowCommands {
    data class InitSession(val processName: String, val responderName: String, val counterparty: String) : FlowCommands()
    data class EstablishSession(val sessionId: String, val processName: String, val senderIdentity: String) : FlowCommands()
    data class AckSession(val sessionId: String, val ok: Boolean) : FlowCommands()

    data class RegisterProcess(val processName: String/*, val actorRef: ActorRef<SessionMessages>*/) : FlowCommands()

    data class ReceiveMessage(val sessionId: String, val msg: SessionMessages) : FlowCommands()
    data class SendCommand(val processName: String, val msg: SessionMessages) : FlowCommands()
}

sealed class SystemMessages : SessionMessages() {
    data class SessionEstablished(val actorRef: ActorRef<SessionCommands>, val counterIdentity: String) : SystemMessages()
}

class FlowDashboard(context: ActorContext<FlowCommands>, val senderIdentity: String, val sendMsg: (String, FlowCommands)->Unit) : AbstractBehavior<FlowCommands>(context) {
    override fun createReceive(): Receive<FlowCommands> {
        return newReceiveBuilder()
            .onMessage(FlowCommands.InitSession::class.java, ::spawnSession)
            .onMessage(FlowCommands.ReceiveMessage::class.java, ::onReceiveMessage)
          //  .onMessage(FlowCommands.SendMessage::class.java, ::onSendMessage)
            .onMessage(FlowCommands.RegisterProcess::class.java, ::onRegisterProcess)
            .onMessage(FlowCommands.EstablishSession::class.java, ::establishSession)
            .onMessage(FlowCommands.AckSession::class.java, ::onAckSession)
            .onMessage(FlowCommands.SendCommand::class.java, ::onSendCommand)
            .build()
    }

    fun onSendCommand(cmd: FlowCommands.SendCommand) : Behavior<FlowCommands> {
        println("FlowDashboard - ${cmd}")

        processes[cmd.processName]?.tell(cmd.msg) ?: throw IllegalArgumentException("Process ${cmd.processName} not found!")
        return this
    }

    val meh = context.spawn(ExamplePersistentBehavior(PersistenceId.ofUniqueId("55")), "ExamplePersistentBehaviour")

    fun onRegisterProcess(cmd: FlowCommands.RegisterProcess) : Behavior<FlowCommands> {
        println("FlowDashboard - ${cmd}")


        processes["TokenExample-Initiator"] = context.spawn(IssueTokensHell.create(context.self, senderIdentity), "TokenExample-Initiator")
        processes["TokenExample"] = context.spawn(TransferTokensProcess.create(context.self, senderIdentity), "TokenExample")
        processes["TokenExample-Responder"] = context.spawn(IssueTokensHell.IssueTokensHellResponder.create(context.self, senderIdentity), "TokenExample-Responder")

        meh.tell(ExamplePersistentBehaviorCommands.ChangeTo("It works?"))

        return this
    }

    private val activeSessions = mutableMapOf<String, ActorRef<SessionCommands>>()
    private val processes = mutableMapOf<String, ActorRef<SessionMessages>>()

    fun onReceiveMessage(param: FlowCommands.ReceiveMessage) : Behavior<FlowCommands> {
       // context.getChild(param.sessionPath.name()).get().tell()
        println("FlowDashboard - ${param}")
        activeSessions[param.sessionId]!!.tell(SessionCommands.Receive(param.msg))
        return this
    }

    fun spawnSession(param: FlowCommands.InitSession) : Behavior<FlowCommands> {
        println("FlowDashboard - ${param}")

        val sessionId = UUID.randomUUID()

        val processRef = processes[param.processName]!!

        activeSessions[sessionId.toString()] = context.spawn(BidirectionalSession.create(processRef, param.counterparty) {
            sendMsg(param.counterparty, FlowCommands.ReceiveMessage(sessionId.toString(), it))
        }, "$sessionId")
        sendMsg(param.counterparty, FlowCommands.EstablishSession(sessionId.toString(), param.responderName, senderIdentity))


        return this
    }

    fun establishSession(param: FlowCommands.EstablishSession) : Behavior<FlowCommands> {
        println("FlowDashboard - ${param}")

        val processRef = processes[param.processName]!!
        activeSessions[param.sessionId] = context.spawn(BidirectionalSession.create(processRef, param.senderIdentity) {
            sendMsg(param.senderIdentity, FlowCommands.ReceiveMessage(param.sessionId, it))
        }, "${param.sessionId}")

        activeSessions[param.sessionId]!!.tell(SessionCommands.Finalize(param.sessionId))
    //    processRef.tell(SystemMessages.SessionEstablished(activeSessions[param.sessionId]!!, param.senderIdentity))
        sendMsg(param.senderIdentity, FlowCommands.AckSession(param.sessionId, true))

        return this
    }

    fun onAckSession(param: FlowCommands.AckSession) : Behavior<FlowCommands> {
        println("FlowDashboard - ${param}")

        if (param.ok) {
            activeSessions[param.sessionId]!!.tell(SessionCommands.Finalize(param.sessionId))
        }

        return this
    }

    companion object {
        fun create(identity:String, sendMsg:  (String, FlowCommands)->Unit) : Behavior<FlowCommands> = Behaviors.setup {
            FlowDashboard(it, identity, sendMsg)
        }
    }
}

sealed class SessionCommands {
    data class Send(val msg: SessionMessages) : SessionCommands()
    data class Receive(val msg: SessionMessages) : SessionCommands()

    data class Finalize(val counterIdentity: String) : SessionCommands()
    class Close : SessionCommands()
}

class BidirectionalSession(
    context: ActorContext<SessionCommands>,
    val sendMsg: (SessionMessages) -> Unit,
    val receivingActor: ActorRef<SessionMessages>,
    val counterIdentity: String
) : AbstractBehavior<SessionCommands>(context) {
    override fun createReceive(): Receive<SessionCommands> {
        return newReceiveBuilder()
            .onMessage(SessionCommands.Send::class.java, ::onMessageSend)
            .onMessage(SessionCommands.Receive::class.java, ::onMessageReceive)
            .onMessage(SessionCommands.Finalize::class.java, ::onFinalize)
            .build()
    }

    fun onFinalize(param: SessionCommands.Finalize) : Behavior<SessionCommands> {
        receivingActor.tell(SystemMessages.SessionEstablished(context.self, counterIdentity))
        return this
    }

    fun onMessageReceive(param: SessionCommands.Receive) : Behavior<SessionCommands> {
        println("Receiving from ${counterIdentity} ${param} telling ${receivingActor.path()}")
        receivingActor.tell(param.msg)
        return this
    }

    fun onMessageSend(param: SessionCommands.Send) : Behavior<SessionCommands> {
        sendMsg(param.msg)
        println("Sending to ${counterIdentity} msg=${param}")
        return this
    }

    companion object {
        fun create(our: ActorRef<SessionMessages>, counterIdentity: String, s: (SessionMessages) -> Unit) : Behavior<SessionCommands> {
            return Behaviors.setup {
                BidirectionalSession(it, s, our, counterIdentity)
            }
        }
    }
}

abstract class MultiPartyProcess(
    context: ActorContext<SessionMessages>,
    val processName: String,
    val flowDashboard: ActorRef<FlowCommands>
) : AbstractBehavior<SessionMessages>(context) {
    protected fun ReceiveBuilder<SessionMessages>.addHandlers() = this.apply {
        this.onMessage(SystemMessages.SessionEstablished::class.java, ::onSession)
    }

    val sessions = mutableMapOf<String, ActorRef<SessionCommands>>()
    val futures = mutableMapOf<String, CompletableFuture<ActorRef<SessionCommands>>>()

    fun onSession(param: SystemMessages.SessionEstablished) : Behavior<SessionMessages> {
        sessions[param.counterIdentity] = param.actorRef
        println("Established session=${param.actorRef} with: ${param.counterIdentity}")

        val onComplete = futures[param.counterIdentity]
        if (onComplete != null) {
            onComplete.complete(param.actorRef)
        } else {
            futures[param.counterIdentity] = CompletableFuture.completedFuture(param.actorRef)
        }
        return this
    }

    fun establishSession(recipient: String, responderProcess: String = processName) : CompletableFuture<ActorRef<SessionCommands>> {
        if (futures[recipient] != null) return futures[recipient]!!

        flowDashboard.tell(FlowCommands.InitSession(processName, responderProcess, recipient))
        futures[recipient] = CompletableFuture()
        return futures[recipient]!!
    }

}

abstract class AutoStateMachine<T : SessionMessages>(
    context: ActorContext<SessionMessages>,
    flowDashboard: ActorRef<FlowCommands>,
    processName: String,
    steps: List<(T)->Unit>
) : MultiPartyProcess(context, processName, flowDashboard) {
    override fun createReceive(): Receive<SessionMessages> {
        return newReceiveBuilder()
            .onMessage(SessionMessages::class.java, ::handleMessage)
            .build()
    }

    var currentExecution = steps.iterator()

    private fun handleMessage(msg: SessionMessages) : Behavior<SessionMessages> {

        if (!currentExecution.hasNext()) {
            return Behaviors.unhandled()
        }
        currentExecution.next()(msg as T)

        return this
    }
}

class SessionPipe<T: SessionMessages, X: SessionMessages>(val now: (T)->SessionPipe<X, *>?) {
    //fun<X : T> next(new: (X)->SessionPipe<*>) = SessionPipe(new)
    companion object {
        fun done() = SessionPipe
    }
}

class NoopMessage : SessionMessages()

abstract class AutoStateMachineCallbackHell<T : SessionMessages>(
    context: ActorContext<SessionMessages>,
    flowDashboard: ActorRef<FlowCommands>,
    processName: String
) : MultiPartyProcess(context, processName, flowDashboard) {
    override fun createReceive(): Receive<SessionMessages> {
        return newReceiveBuilder()
            .onMessage(SessionMessages::class.java, ::handleMessage)
            .build()
    }

    abstract var currentExecution : SessionPipe<T, *>?

    private fun handleMessage(msg: SessionMessages) : Behavior<SessionMessages> {
        println("$processName is Handling message... $currentExecution ... ${currentExecution!!.now}")
        val res = currentExecution?.now?.let {
            it(uncheckedCast(msg)) ?: throw IllegalStateException("Received a message without processing logic!")
        }
        currentExecution = uncheckedCast(res)

        return this
    }
}


class IssueTokensHell(
    context: ActorContext<SessionMessages>,
    flowDashboard: ActorRef<FlowCommands>,
    val myIdentity: String
) : AutoStateMachineCallbackHell<TransferTokensProcess.Commands.Issue>(context, flowDashboard,"TokenExample-Initiator") {
    override var currentExecution: SessionPipe<TransferTokensProcess.Commands.Issue, *>? = uncheckedCast(
        SessionPipe { issue: TransferTokensProcess.Commands.Issue ->
            println("Establishing connection")
            establishSession(issue.recipient, "TokenExample-Responder")

            SessionPipe { sessionMsg: SystemMessages.SessionEstablished ->
                sessionMsg.actorRef.tell(SessionCommands.Send(TransferTokensProcess.Commands.Issuing(myIdentity, issue.amount)))

                SessionPipe<TransferTokensProcess.Commands.SaySomething, NoopMessage> {
                    println(it)
                    null
                }
            }
        }
    )

    class IssueTokensHellResponder(
        context: ActorContext<SessionMessages>,
        flowDashboard: ActorRef<FlowCommands>,
        val myIdentity: String
    ) : AutoStateMachineCallbackHell<SystemMessages.SessionEstablished>(context, flowDashboard,"TokenExample-Responder") {
        override var currentExecution: SessionPipe<SystemMessages.SessionEstablished, *>? = uncheckedCast(SessionPipe { sessionMsg ->
            println("$myIdentity -- session established ${sessionMsg}")
            SessionPipe<TransferTokensProcess.Commands.Issuing, NoopMessage> { issuance: TransferTokensProcess.Commands.Issuing ->
                println("Received issuance ${issuance}")
                sessionMsg.actorRef.tell(SessionCommands.Send(TransferTokensProcess.Commands.SaySomething("$myIdentity says hi!")))
                null
            }
        })

        companion object {
            fun create(flowDashboard: ActorRef<FlowCommands>, identity: String) : Behavior<SessionMessages> {
                return Behaviors.setup {
                    IssueTokensHellResponder(it, flowDashboard, identity)
                }
            }
        }
    }

    companion object {
        fun create(flowDashboard: ActorRef<FlowCommands>, identity: String) : Behavior<SessionMessages> {
            return Behaviors.setup {
                IssueTokensHell(it, flowDashboard, identity)
            }
        }
    }
}

sealed class AutoIssue : SessionMessages() {
    data class Issue(val recipient: String, val amount: Int) : AutoIssue()
    data class Issuing(val resp: String) : AutoIssue()
}

class IssueTokens(
    context: ActorContext<SessionMessages>,
    flowDashboard: ActorRef<FlowCommands>
) : AutoStateMachine<AutoIssue>(context, flowDashboard,"IssueTokens", listOf(
    { msg: AutoIssue ->

    }
))

class TransferTokensProcess(
    context: ActorContext<SessionMessages>,
    flowDashboard: ActorRef<FlowCommands>,
    val myIdentity: String
    ) : MultiPartyProcess(context, "TokenExample", flowDashboard) {

    sealed class Commands : SessionMessages() {
        data class Issue(val recipient: String, val amount: Int) : Commands()
        data class Issuing(val from: String, val amount: Int) : Commands()
        data class Transfer(val amount: Int)
        data class ValidateTransfer(val from: String, val to: String, val amount: Int) : Commands()

        data class SaySomething(val msg: String) : Commands()
    }

    override fun createReceive(): Receive<SessionMessages> {
        return newReceiveBuilder()
            .addHandlers()
            .onMessage(Commands.Issue::class.java, ::onIssue)
            .onMessage(Commands.Issuing::class.java, ::onReceiveIssue)
            .onMessage(Commands.SaySomething::class.java, ::onSaySomething)
            .onMessage(Commands::class.java, ::uncaught)
            .build()
    }

    fun onSaySomething(param: Commands.SaySomething) : Behavior<SessionMessages> {
        println("Saying ${param.msg}")
        return this
    }

    fun uncaught(param: Commands) : Behavior<SessionMessages> {
        println("Uncaught ${param}")
        return this;
    }

    var tokensOwned = mutableMapOf<String, Int>()

    fun onReceiveIssue(param: Commands.Issuing) : Behavior<SessionMessages> {
        println("Received: ${param.amount} from ${param.from}")

        val current = tokensOwned[param.from] ?: 0
        tokensOwned[param.from] = current + param.amount

        establishSession(param.from).thenApply {
            it.tell(SessionCommands.Send(Commands.SaySomething("Hehe")))
        }

        return this
    }

    fun onIssue(cmd: Commands.Issue) : Behavior<SessionMessages> {
        val sessionFuture = establishSession(cmd.recipient)

        Patterns.pipe(sessionFuture, context.executionContext)

        sessionFuture.thenApply {
            it.tell(SessionCommands.Send(Commands.Issuing(myIdentity, cmd.amount)))
            println("Issuing pseudo-tokens to ${cmd.recipient}")
        }

        return this
    }

    companion object {
        fun create(msgSenderRef: ActorRef<FlowCommands>, identity: String) : Behavior<SessionMessages> = Behaviors.setup {
            TransferTokensProcess(it, msgSenderRef, identity)
        }
    }
}

class ExampleApp(
    id: String
)
    : Node(id)
{
    init {
        appSystem.tell(FlowCommands.RegisterProcess("TokenExample"))
    }

    fun issueTokens(recipient: String, validator: String, amount: Int) {
        appSystem.tell(FlowCommands.SendCommand("TokenExample-Initiator", TransferTokensProcess.Commands.Issue(recipient, amount)))
    }
}

open class Node(val id: String) {

    protected fun getNodeFor(recipient: String) = nmap.findLast { it.id == recipient }

    val inboundQueue = LinkedBlockingQueue<FlowCommands>(100)
    val appSystem = ActorSystem.create(FlowDashboard.create(id) { recipient, cmd ->
        send(recipient, cmd)
    }, "appSystem")

    lateinit var nmap : List<Node>

    fun send(recipient: String, cmd: FlowCommands) {
        getNodeFor(recipient)!!.tell(cmd)
    }

    private fun tell(cmd: FlowCommands) {
        inboundQueue.add(cmd)
    }

    private fun processNewMessage(msg: FlowCommands) {
        appSystem.tell(msg)
        println("Processing msg={$msg} at ${id}")
    }

    val stopped = false
    val nodeMain = thread {
        while(!stopped) {
            val res = inboundQueue.take()
            Thread.sleep(1000)
            processNewMessage(res)
        }
    }
}

class Environment {
    val nodes = listOf(
        ExampleApp("PartyA"),
        ExampleApp("PartyB"),
        ExampleApp("PartyC")
    ).let { nodes ->
        nodes.forEach {
            it.nmap = nodes
        }
        nodes
    }
}

sealed class ExamplePersistentBehaviorCommands {
    data class ChangeTo(@JsonProperty("msg") val msg: String) : ExamplePersistentBehaviorCommands()
}

class ExamplePersistentBehavior(id: PersistenceId, val ctx: ActorContext<ExamplePersistentBehaviorCommands>? = null) : EventSourcedBehavior<ExamplePersistentBehaviorCommands, ExamplePersistentBehavior.Events, ExamplePersistentBehavior.State>(id) {

    companion object {
        fun create(id: PersistenceId) : Behavior<ExamplePersistentBehaviorCommands> = Behaviors.setup {
            ExamplePersistentBehavior(id, it)
        }
    }



    sealed class Events {
        data class UpdateMessage(@JsonProperty("newMsg") var newMsg: String) : Events()
    }

    data class State(@JsonProperty("msg") var msg: String)

    override fun emptyState(): State {
        return State("Hello")
    }

    override fun commandHandler(): CommandHandler<ExamplePersistentBehaviorCommands, Events, State> {
        return newCommandHandlerBuilder()
            .forAnyState()
            .onCommand(ExamplePersistentBehaviorCommands.ChangeTo::class.java) { command ->
                println("Received update command with ${command.msg}")
                Effect().persist(Events.UpdateMessage(command.msg))
            }
            .build()
    }

    override fun eventHandler(): EventHandler<State, Events> {
        return newEventHandlerBuilder()
            .forAnyState()
            .onEvent(Events.UpdateMessage::class.java) { state, event: Events.UpdateMessage ->
                val newMsg = "${state.msg} -> ${event.newMsg}"
                println("updating to $newMsg")
                State(newMsg)
            }
            .build()
    }
}

