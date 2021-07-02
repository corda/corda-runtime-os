package net.corda.flow.manager

import net.corda.v5.application.flows.StateMachineRunId
import net.corda.v5.application.identity.Party
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.context.Trace
import net.corda.v5.serialization.SerializedBytes

@CordaSerializable
sealed class FlowEvent(val flowId: StateMachineRunId) {
    @CordaSerializable
    class StartRPCFlow(
        flowId: StateMachineRunId,
        val clientId: String,
        val flowName: String,
        val args: List<Any?>
    ) : FlowEvent(flowId) {
        override fun toString(): String {
            return "StartRPCFlow[flowId=$flowId, clientId=$clientId, flowName=$flowName, args=$args]"
        }
    }

    @CordaSerializable
    class StartInitiatedFlow(
        flowId: StateMachineRunId,
        val message: P2PMessage
    ) : FlowEvent(flowId) {
        override fun toString(): String {
            return "StartInitiatedFlow[flowId=$flowId, message=$message]"
        }
    }

    @CordaSerializable
    class WakeupEvent(flowId: StateMachineRunId) : FlowEvent(flowId) {
        override fun toString(): String {
            return "WakeupEvent[flowId=$flowId]"
        }
    }

    @CordaSerializable
    open class RoutableEvent(
        flowId: StateMachineRunId,
        val source: Party,
        val destination: Party,
        val sessionId: Trace.SessionId,
        val sequenceNo: Int
    ) : FlowEvent(flowId)

    @Suppress("LongParameterList")
    @CordaSerializable
    class P2PMessage(
        flowId: StateMachineRunId,
        val flowName: String,
        source: Party,
        destination: Party,
        sessionId: Trace.SessionId,
        sequenceNo: Int,
        val message: SerializedBytes<*>
    ) : RoutableEvent(flowId, source, destination, sessionId, sequenceNo) {
        override fun toString(): String {
            return "P2PMessage[flowId=$flowId, source=$source, destination=$destination, sessionId=$sessionId, " +
                    "seqNo=$sequenceNo, messageSize=${message.summary}]"
        }
    }

    @Suppress("LongParameterList")
    @CordaSerializable
    class RemoteFlowError(
        flowId: StateMachineRunId,
        source: Party,
        destination: Party,
        sessionId: Trace.SessionId,
        sequenceNo: Int,
        val errorMessage: String
    ) : RoutableEvent(flowId, source, destination, sessionId, sequenceNo) {
        override fun toString(): String {
            return "RemoteFlowError[flowId=$flowId, source=$source, destination=$destination, sessionId=$sessionId, " +
                    "seqNo=$sequenceNo, errorMessage=$errorMessage]"
        }
    }

    @CordaSerializable
    class RPCFlowResult(
        flowId: StateMachineRunId,
        val clientId: String,
        val flowName: String,
        val result: Any?,
        val error: Throwable?
    ) : FlowEvent(flowId) {
        override fun toString(): String {
            return "RPCFlowResult[flowId=$flowId, clientId=$clientId, flowName=$flowName, result=$result, error=${error?.message}]"
        }
    }

}