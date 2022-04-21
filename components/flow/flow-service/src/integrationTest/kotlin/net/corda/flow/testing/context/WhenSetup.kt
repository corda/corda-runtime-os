package net.corda.flow.testing.context

interface WhenSetup {
    fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: net.corda.data.identity.HoldingIdentity,
        cpiId: String,
        args: String
    ): FlowIoRequestSetup

    fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity? = null,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity? = null,
        receivedSequenceNum: Int? = null
    ): FlowIoRequestSetup

    fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        initiatingIdentity: net.corda.data.identity.HoldingIdentity? = null,
        initiatedIdentity: net.corda.data.identity.HoldingIdentity? = null,
        receivedSequenceNum: Int? = null
    ): FlowIoRequestSetup

    fun wakeupEventReceived(flowId: String): FlowIoRequestSetup
}