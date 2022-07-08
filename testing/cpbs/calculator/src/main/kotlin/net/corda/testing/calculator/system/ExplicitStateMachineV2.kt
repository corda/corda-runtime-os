package net.corda.testing.calculator.system

import net.corda.testing.calculator.SessionMessage
import net.corda.v5.base.util.uncheckedCast

abstract class StateMachineOperationV2<StateType>() {
    abstract val listeningFor: Set<Class<*>>
    abstract val contextStateType: Class<*>

    internal var injServices: StateMachineServices? = null
    fun services() = injServices ?: throw IllegalStateException("services are only accessible while processing messages!")


    abstract fun processMessage(msg: SessionMessage, state: StateType) : StateType
    abstract fun onError(state: StateType, err: ActorError)

    open fun onFocus(state: StateType) {}
    open fun leaveFocus(state: StateType) {}

    abstract fun getInitialContextState() : StateType


    internal fun onErrorWrapper(err:ActorError, state: StateType){
        onError(state, err)
        if (!err.handled()) {
            throw IllegalStateException("Error must be handled satisfactory")
        }
    }

    companion object {
    }
}

class Operation<Type : Any> private constructor(
    override val contextStateType: Class<Type>,
    val messageProcessors: Map<Class<*>, BuilderDSL.MessageProcessor<Type, SessionMessage>>,
    val onFocus: (StateMachineOperationV2<Type>.(Type)->Unit)?,
    val leaveFocus: (StateMachineOperationV2<Type>.(Type)->Unit)?,
    val onErrorInj: StateMachineOperationV2<Type>.(Type, ActorError) -> Unit,
    val checkTransition: StateMachineOperationV2<Type>.(Type)->StateHeader?,
    val getInitialContextStateInj: ()->Type
) : StateMachineOperationV2<Type>()  {

    override val listeningFor: Set<Class<*>> = messageProcessors.map { (key, v) -> key }.toSet()

    private fun maybeTransition(state: Type) {
        checkTransition.invoke(this, state)?.let {
            services().transition(it)
        }
    }

    override fun processMessage(msg: SessionMessage, state: Type): Type {
        messageProcessors[msg.javaClass]?.consumer?.invoke(this, state, msg)
            ?: throw java.lang.IllegalArgumentException("Supervisor should not allow this message...")

        maybeTransition(state)

        return state
    }

    override fun onError(state: Type, err: ActorError) { onErrorInj.invoke(this, state, err) }
    override fun onFocus(state: Type) {
        onFocus?.invoke(this, state)
        maybeTransition(state)
    }
    override fun leaveFocus(state: Type) {
        leaveFocus?.invoke(this, state)
    }

    override fun getInitialContextState() = getInitialContextStateInj()

    class BuilderDSL<StateType: Any> internal constructor(val initialContextState: StateType) {

        data class MessageProcessor<StateType, MessageType: SessionMessage>(
            val consumer: StateMachineOperationV2<StateType>.(StateType, MessageType) -> Unit
        )

        private var onFocus : (StateMachineOperationV2<StateType>.(StateType) -> Unit)? = null
        private var leaveFocus: (StateMachineOperationV2<StateType>.(StateType) -> Unit)? = null

        private val messageProcessors : MutableMap<Class<*>, MessageProcessor<*, *>> = mutableMapOf()

        fun onTransitionFocus(onFocus: StateMachineOperationV2<StateType>.(StateType)->Unit) = this.apply {
            this.onFocus = onFocus
        }

        fun onTransitionUnfocus(outOfFocus: StateMachineOperationV2<StateType>.(StateType)->Unit) = this.apply {
            this.leaveFocus = outOfFocus
        }

        fun<MsgType: SessionMessage> onMessage(
            type: Class<MsgType>,
            consumer: StateMachineOperationV2<StateType>.(StateType, MsgType)->Unit
        ) = this.apply {
            messageProcessors[type] = MessageProcessor(consumer)
        }

        private lateinit var shouldTransitionChecker : StateMachineOperationV2<StateType>.(StateType)->StateHeader?

        fun transitionCheck( check: StateMachineOperationV2<StateType>.(StateType)->StateHeader?) = this.apply {
            shouldTransitionChecker = check
        }


        fun build(
            operationHeader: String,
            errCheck: StateMachineOperationV2<StateType>.(StateType, ActorError)->Unit
        ) : Pair<StateHeader, Operation<StateType>> {
            val contextStateType = initialContextState.javaClass
            val getFreshContext = fun() : StateType {
                //must be copy
                return initialContextState
            }

            if (!operationHeader.contains(":")) {
                throw java.lang.IllegalArgumentException(
                    "Invalid Header! Please use the following header format - Operation-Name:VERSION"
                )
            }

            val version = operationHeader.substringAfter(":")
            val name = operationHeader.substringBefore(":")

            return Pair(StateHeader(name, version), Operation(
                contextStateType,
                uncheckedCast(messageProcessors.toMap()),
                onFocus,
                leaveFocus,
                errCheck,
                shouldTransitionChecker,
                getFreshContext
            ))
        }
    }

    companion object {
        fun<StateType : Any> define(initialContextState: StateType) = BuilderDSL(initialContextState)
        fun<StateType: Any> adapter(header: String, context: StateType, next: StateHeader, adaptOnFocus: StateMachineOperationV2<StateType>.(StateType)->Unit)
        = define(context)
            .onTransitionFocus(adaptOnFocus)
            .transitionCheck { next }
            .build(header) {state, err ->
                println("Err encountered: $err")
            }
    }
}

data class TypeAwareQueue(val map: MutableMap<Class<*>, MutableList<SessionMessage>>)
    : MutableMap<Class<*>, MutableList<SessionMessage>> by map {

    /*fun nextFrom(types: Set<Class<*>>) : SessionMessage {
        return types.first()
    }*/
}

abstract class ExplicitStateMachineV2<StateType>(
    initialContext: StateType,
    private val persistence: StateMachinePersistence<StateType>
) {

    private lateinit var states : Map<StateHeader, StateMachineOperationV2<StateType>> //= mutableMapOf()
    private val messageQueue = TypeAwareQueue(mutableMapOf())

    private lateinit var currentOperation: StateHeader

    private var context = initialContext

    var generateServices = {
        AsyncQueuedServices()
    }


    private fun currentScope(
        scopedAccess : (StateMachineOperationV2<StateType>)->Unit
    ) {
        val currentOp = states[currentOperation]!!
        val scopedServices = generateServices()

        try {
            currentOp.injServices = scopedServices

            scopedAccess(currentOp)
        } finally {
            currentOp.injServices = null
            persistServices(scopedServices)
            processServiceQueue(scopedServices)
        }
    }

    private fun focus() = currentScope {
        it.onFocus(context)
    }
    private fun unfocus() = currentScope {
        it.leaveFocus(context)
    }

    private fun executeTransition(newHeader: StateHeader) {
        unfocus()

        println("Unfocused $currentOperation")
        currentOperation = newHeader
        println("Focusing $currentOperation")

        focus()
    }

    private fun processServiceQueue(services: AsyncQueuedServices) {
        services.transition?.first?.let {
            executeTransition(newHeader = it)
        }
    }


    enum class ConsumeResult {
        CONSUMED,
        NOT_LISTENING
    }

    fun<Msg: SessionMessage> receive(msg: Msg) : ConsumeResult{
        var status = ConsumeResult.CONSUMED
        currentScope { op ->
            if (op.listeningFor.contains(msg.javaClass)) {
                context = op.processMessage(msg, context)
            } else {
                status = ConsumeResult.NOT_LISTENING
            }
        }
        return status
    }

    private fun persistServices(services: AsyncQueuedServices) {
        persistence.persistState(context)

        services.sessions.forEach { (target, container) ->
            container.messages.forEach { msg ->
                persistence.queueOutboundMessage(target, msg)
            }
        }
    }

    fun initialize() {
        println("Initializing ...")
        states = registerHandlers()
        currentOperation = StateHeader("Init", "latest")
        states[currentOperation] ?: throw IllegalStateException("State machine entry point is undefined!")
        println("Initialized!")

        focus()
    }

    abstract fun registerHandlers() : Map<StateHeader, StateMachineOperationV2<StateType>>
}

