package net.corda.testing.calculator.system

import net.corda.testing.calculator.SessionMessage
import java.util.UUID

data class StateHeader(val name: String, val version: String) {
    override fun toString(): String {
        return "$name:$version"
    }

    //Only if created using adaptWith
    var startingAt : StateHeader? = null
    var endingAt : StateHeader? = null

    fun adaptWith(other: StateHeader) =
        StateHeader("$name-${other.name}", "$version-${other.version}").let {
            it.startingAt = this
            it.endingAt = other

            return@let it
        }
}
fun String.toStateHeader()  : StateHeader {
    val version = this.substringAfter(":")
    val name = this.substringBefore(":")
    return StateHeader(name, version)
}

interface MessageSession {
    fun send(msg: SessionMessage) : MessageSession
}

interface StateMachineServices {
    fun transition(targetOp: StateHeader, msg: SessionMessage? = null) : Unit
    fun transition(targetOpStr: String, msg: SessionMessage? = null) : Unit {
        val version = targetOpStr.substringAfter(":")
        val name = targetOpStr.substringBefore(":")
        transition(StateHeader(name, version), msg)
    }
    fun scheduleShutdown(res: Any) : Unit {}

    fun session(target: String) : MessageSession

    fun getTx(stateRef: String) = Pair("no-inputs", stateRef)

    fun spawn(targetOp: StateHeader, msg: SessionMessage? = null) : Unit {}
    fun myIdentity() : String { return "None"}

    fun sign(msg: String) = Signature("Signed={$msg}", myIdentity())
}

abstract class ActorError {
    enum class Severity {
        EXPECTED,
        WARNING,
        FATAL
    }
    abstract val severity : Severity

    open fun handled() : Boolean = false
}

abstract class StateMachineOperation<StateType>() {
    abstract val listeningFor: Set<Class<*>>

    internal var injServices: StateMachineServices? = null
    fun services() = injServices ?: throw IllegalStateException("services are only accessible while processing messages!")



    abstract fun processMessage(msg: SessionMessage, state: StateType) : StateType
    abstract fun onError(err: ActorError)

    internal fun onErrorWrapper(err:ActorError){
        onError(err)
        if (!err.handled()) {
            throw IllegalStateException("Error must be handled satisfactory")
        }
    }

    companion object {
        fun<Type> redirect(next: StateHeader) = object : StateMachineOperation<Type>() {
            override val listeningFor = emptySet<Class<*>>()
            override fun onError(err: ActorError) {

            }

            override fun processMessage(msg: SessionMessage, state: Type): Type {
                services().transition(next)
                return state
            }

        }
    }
}

class AsyncQueuedServices : StateMachineServices {

    class MessagesContainer(val target: String): MessageSession {
        val messages = mutableListOf<SessionMessage>()
        override fun send(msg: SessionMessage) = this.apply {
            messages.add(msg)
        }
    }

    val sessions = mutableMapOf<String, MessagesContainer>()
    override fun session(target: String): MessageSession {
        return sessions.getOrPut(target) {
            MessagesContainer(target)
        }
    }

    var transition : Pair<StateHeader, SessionMessage?>? = null
    override fun transition(targetOp: StateHeader, msg: SessionMessage?) {
        transition = Pair(targetOp, msg)
    }
}

interface StateMachinePersistence<StateType> {
    fun persistState(state: StateType) : Unit
    fun queueOutboundMessage(target: String, msg: SessionMessage)
}

open class TransitionMessage: SessionMessage()

abstract class ExplicitStateMachine<StateType>(initialContext: StateType, val persistence: StateMachinePersistence<StateType>) {
    private lateinit var states : Map<StateHeader, StateMachineOperation<StateType>> //= mutableMapOf()
    private val messageQueue: MutableMap<Class<*>, MutableList<SessionMessage>> = mutableMapOf()

    private lateinit var currentOperation: StateHeader

    private var context = initialContext

    internal fun<Msg: SessionMessage> receive(msg: Msg) {
        val currentOp = states[currentOperation]!!
        if (currentOp.listeningFor.contains(msg.javaClass)) {
            val scopedServices = AsyncQueuedServices()
            currentOp.injServices = scopedServices
            context = currentOp.processMessage(msg, context)
            currentOp.injServices = null
            processServiceQueue(scopedServices)
        } else {
            messageQueue.getOrPut(msg.javaClass) { mutableListOf() }.add(msg)
        }
    }

    private fun processServiceQueue(services: AsyncQueuedServices) {
        currentOperation = services.transition?.first ?: currentOperation

        persistence.persistState(context)

        services.sessions.forEach { (target, container) ->
            container.messages.forEach { msg ->
                persistence.queueOutboundMessage(target, msg)
            }
        }

        if (services.transition?.second != null) {
            receive(services.transition!!.second!!)
        }
    }

    internal fun initialize() {
        states = registerHandlers()
        currentOperation = StateHeader("Init", "latest")
        states[currentOperation] ?: throw IllegalStateException("State machine entry point is undefined!")
    }

    abstract fun registerHandlers() : Map<StateHeader, StateMachineOperation<StateType>>
}

data class Signature(val pseudo: String, val identity: String) {
    fun verify(msg: String) = "Signed={$msg}" == pseudo
}

class TransferTokensFlow(persistence: StateMachinePersistence<ContextState>)
    : ExplicitStateMachine<TransferTokensFlow.ContextState>(ContextState.Initiator(), persistence) {

    sealed class ContextState {
        data class Initiator(
            val counterParty: String = "none",
            val validator: String = "none",
            val tokens: MutableList<String> = mutableListOf(),
            var transaction: Pair<String, String> = Pair("no-input", "no-output"),
            val signatures: MutableList<Signature> = mutableListOf(),
            var notarization: Signature = Signature("none", "unknown"),
            val proverState: MutableMap<String, Pair<String, String>> = TxResolution.Prover.defaultState()
        ) : ContextState()

        data class Receiver(
            var counterParty: String = "none",
            var pendingTx: Pair<String, String> = Pair("none", "none"),
            val signatures: MutableList<Signature> = mutableListOf(),
            var notarization: Signature = Signature("none", "unknown"),
            val verifierState: MutableSet<String> = TxResolution.Verifier.defaultState()
        ) : ContextState()
    }

    data class FlowInitMessage(
        val counterParty: String,
        val validator: String,
        val amount: Int
    ) : SessionMessage()

    data class ProposeTransferMessage(
        val from: String, //party
        val amount: Int,
        val transaction: Pair<String, String>
    ) : SessionMessage()

    override fun registerHandlers(): Map<StateHeader, StateMachineOperation<ContextState>> {
        return mapOf(
            StateHeader("Init", "latest")
                    to object : StateMachineOperation<ContextState>() {
                override val listeningFor = setOf(
                    FlowInitMessage::class.java,
                    ProposeTransferMessage::class.java
                )

                override fun onError(err: ActorError) {
                    println(err.severity)
                }

                override fun processMessage(msg: SessionMessage, state: ContextState): ContextState {
                    return when (msg) {
                        is FlowInitMessage -> {
                            services().transition("SelectTokens:latest")
                            ContextState.Initiator(
                                counterParty = msg.counterParty,
                                validator = msg.validator,
                            )
                        }
                        is ProposeTransferMessage -> {
                            services().transition(TxResolution.Verifier.header(), TransitionMessage())
                            ContextState.Receiver(
                                msg.from,
                                msg.transaction
                            )
                        }
                        else -> {
                            throw java.lang.IllegalArgumentException("This should be unreachable :)")
                        }
                    }
                }
            },
            StateHeader("SelectTokens", "latest")
                    to GenericSelectTokensOperation(next = StateHeader("BuildTx", "latest")),
            StateHeader("BuildTx", "latest")
                    to object: StateMachineOperation<ContextState>() {
                override val listeningFor = emptySet<Class<*>>()

                override fun onError(err: ActorError) {}

                override fun processMessage(msg: SessionMessage, state: ContextState): ContextState {
                    state as ContextState.Initiator

                    //pretend we build transaction here

                    val txInput = state.tokens.joinToString(",")
                    val txOutput = "owner - ${state.counterParty}"

                    val tx = Pair(txInput, txOutput)

                    services()
                        .session(state.counterParty)
                        .send(ProposeTransferMessage(services().myIdentity(), state.tokens.size, tx))

                    services().transition(TxResolution.Prover.header(), TransitionMessage())
                    return state.apply {
                        transaction = Pair(txInput, txOutput)
                    }
                }
            },
            TxResolution.Prover.header()
                    to TxResolution.Prover(next = StateHeader("Finalize", "latest")),
            TxResolution.Verifier.header()
                    to TxResolution.Verifier(next = StateHeader("Finalize", "latest"))
        )
    }
}


sealed class TxResolution : StateMachineOperation<TransferTokensFlow.ContextState>() {
    data class Ask(val inputStateId: String) : SessionMessage()
    data class Prove(val inputStateId: String, val transaction: Pair<String, String>) : SessionMessage()
    class Done : SessionMessage()

    class Prover(val next: StateHeader) : TxResolution() {
        override val listeningFor = setOf(
            TransitionMessage::class.java,
            Ask::class.java,
            Done::class.java
        )

        override fun onError(err: ActorError) { }

        override fun processMessage(msg: SessionMessage, state: TransferTokensFlow.ContextState)
        : TransferTokensFlow.ContextState {
            state as TransferTokensFlow.ContextState.Initiator

            when (msg) {
                is TransitionMessage -> return initialize(state)
                is Done -> services().transition(next, TransitionMessage())
                is Ask -> services()
                    .session(state.counterParty)
                    .send(Prove(msg.inputStateId, state.proverState[msg.inputStateId]!!))

                else -> throw java.lang.IllegalArgumentException(
                    "Not listening for this message type; This code should be unreachable unless Supervisor is drunk!"
                )
            }
            return state
        }

        private fun initialize(state: TransferTokensFlow.ContextState) = (state as TransferTokensFlow.ContextState.Initiator).apply {
            //Generate proof for state.transaction
            state.transaction.first.split(",").forEach { stateRef ->
                val provingTx = services().getTx(stateRef)
                state.proverState[stateRef] = provingTx
            }
        }

        companion object {
            fun header() = StateHeader("TxResolution.Prover", "latest")
            fun defaultState() = mutableMapOf<String, Pair<String, String>>()
        }
    }

    class Verifier(val next: StateHeader) : TxResolution() {
        override val listeningFor = setOf(
            TransitionMessage::class.java,
            Prove::class.java
        )
        override fun onError(err: ActorError) {}

        override fun processMessage(msg: SessionMessage, state: TransferTokensFlow.ContextState): TransferTokensFlow.ContextState {
            (state as TransferTokensFlow.ContextState.Receiver)
            when (msg) {
                is TransitionMessage -> {
                    val inputs = getInputsOfTx(state.pendingTx)
                    state.verifierState.addAll(inputs)
                    queryInputs(state.counterParty, inputs)
                }
                is Prove -> {
                    if (msg.transaction.first == "no-inputs") {
                        //Pseudo issuance
                        state.verifierState.remove(msg.transaction.second)
                        if (state.verifierState.isEmpty())
                            services().transition(next, TransitionMessage())
                    } else {
                        val inputs = getInputsOfTx(msg.transaction)
                        state.verifierState.addAll(inputs)
                        queryInputs(state.counterParty, inputs)
                    }
                }
            }

            return state
        }

        private fun queryInputs(from: String, inputs: Collection<String>) {
            services().session(from).let { session ->
                inputs.forEach { stateRef ->
                    session.send(Ask(stateRef))
                }
            }
        }


        private fun getInputsOfTx(tx: Pair<String, String>) = tx.first.split(",")

        companion object {
            fun header() = StateHeader("TxResolution.Verifier", "latest")
            fun defaultState() = mutableSetOf<String>()
        }
    }
}



class GenericSelectTokensOperation(val next: StateHeader) : StateMachineOperation<TransferTokensFlow.ContextState>() {

    data class SelectTokensTransition(
        val amount: Int,
    ) : TransitionMessage()
    override val listeningFor = setOf(
        SelectTokensTransition::class.java
    )

    override fun onError(err: ActorError) {

    }

    override fun processMessage(
        msg: SessionMessage,
        state: TransferTokensFlow.ContextState
    ): TransferTokensFlow.ContextState {
        msg as SelectTokensTransition
        val tokens = 0.rangeTo(msg.amount).map {
            UUID.randomUUID().toString()
        }

        services().transition(next, TransitionMessage())
        return (state as TransferTokensFlow.ContextState.Initiator).apply {
            this.tokens.addAll(tokens)
        }
    }
}