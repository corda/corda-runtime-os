package net.corda.flow.testing.context

import net.corda.data.identity.HoldingIdentity

interface WhenSetup {
    fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: HoldingIdentity,
        cpiId: String,
        args: String
    ): FlowIoRequestSetup

    fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun sessionCloseEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun wakeupEventReceived(flowId: String): FlowIoRequestSetup
}