import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import net.corda.testing.calculator.SessionMessage
import net.corda.testing.calculator.system.*
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread



class ExplicitStateMachineV2Test {



    @Test
    fun `ActorSystem test`() {

        val initiator = { ctx: (InitiatorCtx), persistence: StateMachinePersistence<InitiatorCtx> ->
            ExampleFlow.Initiator(persistence, ctx)
        }

        val ctxGen = {
            InitiatorCtx("PartyB", 3)
        }

        val respoder = { ctx: ExampleFlow.Responder.State, persistence: StateMachinePersistence<ExampleFlow.Responder.State> ->
            ExampleFlow.Responder(persistence, ctx)
        }

        val rCtxGen = {
            ExampleFlow.Responder.State()
        }

        println("Starting system")

        lateinit var systemA :  ActorSystem<SessionMessage>
        lateinit var systemB :  ActorSystem<SessionMessage>


        systemA = ActorSystem.create(Behaviors.setup {
            StateMachineHypervisor("init", initiator, ctxGen) { target, msg ->
                if (target == "PartyB") {
                    msg.apply {
                        authenticatedSender = "PartyA"
                    }
                    systemB.tell(HypervisorCommands.DeliverMessage(msg))
                }
            }
        }, "testSystem")

        systemB = ActorSystem.create(Behaviors.setup {
            StateMachineHypervisor("resp", respoder, rCtxGen) { target, msg ->
                if (target == "PartyA") {
                    msg.apply {
                        authenticatedSender = "PartyB"
                    }
                    systemA.tell(HypervisorCommands.DeliverMessage(msg))
                }
            }
        }, "testSystem")

        println("Telling to initialize?")
        systemB.tell(HypervisorCommands.Initialize())
        systemA.tell(HypervisorCommands.Initialize())

        println("Going to sleep")
        Thread.sleep(5000)
    }

    @Test
    fun `basic end to end test`() {

        lateinit var initiator : ExampleFlow.Initiator
        lateinit var responder : ExampleFlow.Responder

        lateinit var responderPersistence : StateMachinePersistence<ExampleFlow.Responder.State>
        lateinit var initiatorPersistence : StateMachinePersistence<InitiatorCtx>

        fun fixSessionMessageForTest(sender: String, msg: SessionMessage) : SessionMessage{
            msg.sessionId = "1"
            msg.authenticatedSender = sender
            return msg
        }

        val initializeLock = CompletableFuture<Boolean>()

        val initiatorQueue = LinkedBlockingQueue<SessionMessage>()
        val responderQueue = LinkedBlockingQueue<SessionMessage>()

        initiatorPersistence = object : StateMachinePersistence<InitiatorCtx> {
            override fun persistState(state: InitiatorCtx) {
                //meh
            }

            override fun queueOutboundMessage(target: String, msg: SessionMessage) {
                if (target == "PartyB") {
                    println("sending to PartyB")
                    responderQueue.add(fixSessionMessageForTest("PartyA", msg))
/*
                    thread {
                        initializeLock.get()
                        Thread.sleep(500)
                        println("PartyB about to receive")
                    } */
                } else {
                    println("Weird target ${target} in test")
                    throw java.lang.IllegalArgumentException("This test should not have any other targets")
                }
            }
        }

        responderPersistence = object : StateMachinePersistence<ExampleFlow.Responder.State> {
            override fun persistState(state: ExampleFlow.Responder.State) {
                //Nothing needed here.
            }

            override fun queueOutboundMessage(target: String, msg: SessionMessage) {
                //normally we'd persist the message on the queue and send it as a side effect.
                val carpentedMsg = msg.apply {
                    sessionId = "1"
                }
                if (target == "PartyA") {
                    println("sending to PartyA")
                    initiatorQueue.add(fixSessionMessageForTest("PartyB", msg))
/*
                    thread {
                        initializeLock.get()
                        Thread.sleep(500)
                        println("PartyA about to receive")
                    }*/
                } else {
                    println("Weird target ${target} in test")
                    throw java.lang.IllegalArgumentException("This test should not have any other targets")
                }
            }
        }

        val finished = AtomicBoolean(false)

        initiator = ExampleFlow.Initiator(initiatorPersistence, InitiatorCtx(
            "PartyB",
            5,
            mutableSetOf()
        )).apply {
            initialize()
        }
        responder = ExampleFlow.Responder(responderPersistence, ExampleFlow.Responder.State()).apply {
            initialize()
        }

        thread {
            println("PartyA processing")
            while (!finished.get()) {
                val msg = initiatorQueue.take()
                initiator.receive(msg)
            }
        }

        thread {
            println("PartyB processing..")
            while (!finished.get()) {
                val msg = responderQueue.take()
                responder.receive(msg)
            }
        }
        initializeLock.complete(true)

        Thread.sleep(100000)
        finished.set(true)
    }
}

sealed class Context


data class InitiatorCtx(
    override var counterParty: String,
    override val amount: Int,
    override val tokens: MutableCollection<String> = mutableSetOf(),
    override var transaction: Pair<String, String> = Pair("no-inputs", "invalid"),
    override var refToTx: MutableMap<String, Pair<String, String>> = mutableMapOf(),
    override var isDone: Boolean = false,
    val csState : CollectSignatures.State = CollectSignatures.State()
) : Context(), SelectTokens.State, BackchainResolution.Prover.State, CollectSignatures.IState by csState


sealed class ExampleError: ActorError() {
    data class NoResponder(override val severity: Severity = Severity.FATAL) : ExampleError() {
        override fun handled() = true
    }
}

sealed class ExampleFlow<Type: Any>(
    persistence: StateMachinePersistence<Type>,
    ctx: Type
) : ExplicitStateMachineV2<Type>(ctx, persistence) {

    sealed class ExampleMessages : SessionMessage() {
        data class AnnounceTransfer(val pendingTx: Pair<String, String>) : ExampleMessages()
    }

    class Initiator(
        persistence: StateMachinePersistence<InitiatorCtx>,
        val ctx: InitiatorCtx = InitiatorCtx("nobody", 5, mutableListOf()),
    ): ExampleFlow<InitiatorCtx>(persistence, ctx) {

        override fun registerHandlers() = mapOf(
            SelectTokens.operation("Init:latest", next = StateHeader("BuildTx", "0.1"), ctx),
            Operation
                .define(ctx)
                .onTransitionFocus {
                    val txInputs = it.tokens.joinToString(",")
                    val txOutputs = "owner = ${it.counterParty}"
                    val tx = Pair(txInputs, txOutputs)
                    it.transaction = tx

                    services().session(it.counterParty).send(ExampleMessages.AnnounceTransfer(tx))
                }.transitionCheck {
                    BackchainResolution.Prover.header()
                }.build("BuildTx:0.1") {state, err ->
                    if (err is ExampleError.NoResponder) {
                        services().scheduleShutdown(err)
                    }
                },
            BackchainResolution.Prover.operation(ctx = ctx, next = "Br-Prover-CSigs-Adapter:0.1".toStateHeader()),
            Operation
                .adapter("Br-Prover-CSigs-Adapter:0.1",
                    ctx,
                    next = CollectSignatures.header()
                ) {
                    it.csState.txRoot = "{${it.transaction.first}:${it.transaction.second}}"
                },
            CollectSignatures.operation(ctx = ctx, next = StateHeader("WIP", "WIP"))
        )

    }

    class Responder(
        persistence: StateMachinePersistence<State>,
        val ctx: State
    ) : ExampleFlow<Responder.State>(persistence, ctx) {
        data class State(
            val csState: CollectSignatures.State = CollectSignatures.State(),
            override val verifierState: BackchainResolution.Verifier.State = BackchainResolution.Verifier.State()
        ) : CollectSignatures.IState by csState, BackchainResolution.Verifier.IState

        override fun registerHandlers() = mapOf(
            Operation.define(ctx)
                .onMessage(ExampleMessages.AnnounceTransfer::class.java) { state, msg ->
                    state.verifierState.txToResolve = msg.pendingTx
                    state.verifierState.counterParty = msg.sender()
                }
                .transitionCheck { state->
                    BackchainResolution.Verifier.header().takeIf {
                        state.verifierState.counterParty != "unknown"
                    }
                }
                .build("Init:latest") {state, err -> },
            BackchainResolution.Verifier.operation(next = adapterHeader, ctx= ctx),
            Operation.adapter(
                adapterHeader.toString(),
                ctx,
                next = adapterHeader.endingAt!!
            ) {
                val tx = it.verifierState.txToResolve
                it.csState.txRoot = "{${tx.first}:${tx.second}}"
                listOf(
                    services().myIdentity(),
                    it.verifierState.counterParty
                ).let { participants ->
                    it.csState.remainingSignatures.addAll(participants)
                }
            },
            CollectSignatures.operation(ctx = ctx, next = StateHeader("WIP", "WIP"))
        )

        companion object {
            val adapterHeader = BackchainResolution.Verifier.header().adaptWith(CollectSignatures.header())
        }
    }

}

class CollectSignatures {
    data class ProvideSignature(val sig: Signature) : SessionMessage()

    interface IState {
        val remainingSignatures: MutableSet<String> //= mutableSetOf(), //String == Party
        val collectedSignatures: MutableList<Signature> //= mutableListOf(),
        var txRoot : String //= "invalid"// "{INPUT_ID,INPUT_ID,INPUT_ID:OUTPUT}" == txRoot
    }

    data class State(
        override val remainingSignatures: MutableSet<String> = mutableSetOf(), //String == Party
        override val collectedSignatures: MutableList<Signature> = mutableListOf(),
        override var txRoot : String = "invalid"// "{INPUT_ID,INPUT_ID,INPUT_ID:OUTPUT}" == txRoot
    ) : IState

    companion object {
        fun name() = "${CollectSignatures::class.java.name}"
        fun header() = StateHeader(name(), "0.1")
        fun<StateType : IState>
                operation(header: String = "${header()}", ctx: StateType, next: StateHeader) =
            Operation.define(ctx)
                .onTransitionFocus {
                    val sig = services().sign(it.txRoot)
                    it.remainingSignatures.remove(sig.identity)
                    it.collectedSignatures.add(sig)
                    it.remainingSignatures.forEach { party->
                        services()
                            .session(party)
                            .send(ProvideSignature(sig))
                    }
                }.onMessage(ProvideSignature::class.java) {state, msg ->
                    if (msg.sig.verify(state.txRoot)) {
                        state.remainingSignatures.remove(msg.sig.identity)
                        state.collectedSignatures.add(msg.sig)
                    }
                }.transitionCheck {state ->
                    next.takeIf { state.remainingSignatures.size == 0 }
                }.build(header) { state, err ->
                    //unhandled for now
                }
    }
}

sealed class BackchainResolution {

    class Prover : BackchainResolution() {

        data class Prove(val tx: Pair<String, String>): SessionMessage()

        interface State {
            var transaction : Pair<String, String>
            var refToTx: MutableMap<String, Pair<String, String>>
            var isDone : Boolean
            var counterParty: String
        }

        companion object {
            fun name() = "${Prover::class.java.name}"
            fun header() = StateHeader(name(), "0.1")
            fun<StateType : State> operation(header: String = "${header()}", ctx: StateType, next: StateHeader) =
                Operation
                    .define(ctx)
                    .onTransitionFocus {
                        it.transaction.first.split(",").forEach { ref ->
                            it.refToTx[ref] = services().getTx(ref)
                        }
                    }
                    .onMessage(Verifier.Done::class.java) { state, msg ->
                        if (msg.res == false) {
                            println("counterparty is drunk :< ")
                        } else {
                            state.isDone = true
                        }
                    }
                    .onMessage(Verifier.Ask::class.java) { state, msg ->
                        if (msg.stateRef != "no-inputs") {
                            val proof = Prove(state.refToTx[msg.stateRef]!!)

                            services()
                                .session(state.counterParty)
                                .send(proof)
                        }
                    }
                    .transitionCheck {state -> next.takeIf { state.isDone } }
                    .build(header) {state, err ->

                    }
        }
    }

    class Verifier : BackchainResolution() {

        data class Ask(val stateRef: String) : SessionMessage()
        data class Done(val res : Boolean) : SessionMessage()

        data class State(
            var txToResolve: Pair<String, String> = Pair("", ""),
            val unresolvedStateRefs : MutableSet<String> = mutableSetOf(),
            var counterParty: String = "unknown"
        )

        interface IState { //bad naming here wow
            val verifierState: State
        }

        companion object {
            fun name() = "${Verifier::class.java.name}"
            fun header() = StateHeader(name(), "0.1")

            fun <StateType: IState> operation(header: String = "${header()}", next: StateHeader, ctx: StateType) =
                Operation
                    .define(ctx)
                    .onTransitionFocus {
                        val unresolvedInputs = it.verifierState.txToResolve.first.replace("no-inputs", "").split(",")
                        if (unresolvedInputs.isNotEmpty()) {
                            it.verifierState.unresolvedStateRefs.addAll(unresolvedInputs)
                            services().session(it.verifierState.counterParty).let { session ->
                                unresolvedInputs.forEach { ref ->
                                    session.send(Ask(ref))
                                }
                            }
                        } else {
                            services()
                                .session(it.verifierState.counterParty)
                                .send(Done(true))
                        }
                    }.onMessage(Prover.Prove::class.java) {state, msg ->
                        val success = state.verifierState.unresolvedStateRefs.remove(msg.tx.second)
                        if (success) {
                            val unresolvedStateRefs = msg.tx.first.replace("no-inputs", "").split(",")

                            if (msg.tx.first != "no-inputs") {
                                if (unresolvedStateRefs.isNotEmpty()) {
                                    state.verifierState.unresolvedStateRefs.addAll(unresolvedStateRefs)
                                    services().session(state.verifierState.counterParty).let { session ->
                                        unresolvedStateRefs.forEach { ref ->
                                            session.send(Ask(ref))
                                        }
                                    }
                                }
                            }
                            if (state.verifierState.unresolvedStateRefs.isEmpty()) {
                                services()
                                    .session(state.verifierState.counterParty)
                                    .send(Done(true))
                            }
                        }
                    }
                    .transitionCheck {state ->
                        next.takeIf { state.verifierState.unresolvedStateRefs.isEmpty() }
                    }
                    .build(header) { state, err ->
                        //no error handling yet
                    }

        }
    }
}

class SelectTokens {

    interface State {
        val amount: Int
        val tokens: MutableCollection<String>
    }

    companion object {
        fun<StateType : State> operation(header: String = "SelectTokens:0.1", next: StateHeader, ctx: StateType) =
            Operation
                .define(ctx)
                .onTransitionFocus {
                    it.tokens.addAll(0.rangeTo(it.amount).map { UUID.randomUUID().toString() })
                }.transitionCheck {state->
                    return@transitionCheck next.takeIf { state.tokens.size >= state.amount }
                }.build(header) {state, actorError ->
                    println("Nothing to go wrong here.")
                }
    }
}