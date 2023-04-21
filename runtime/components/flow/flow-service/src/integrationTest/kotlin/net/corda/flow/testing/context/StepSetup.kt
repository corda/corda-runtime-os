package net.corda.flow.testing.context

import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.OperationalStatus

interface StepSetup {

    val initiatedIdentityMemberName: MemberX500Name

    fun virtualNode(cpiId: String, holdingId: HoldingIdentity, flowOperationalStatus: OperationalStatus = OperationalStatus.ACTIVE)

    fun cpkMetadata(cpiId: String, cpkId: String, cpkChecksum: SecureHash)

    fun sandboxCpk(cpkFileChecksum: SecureHash)

    fun membershipGroupFor(owningMember: HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity)

    fun flowConfiguration(key: String, value: Any)

    fun initiatingToInitiatedFlow(protocol: String, initiatingFlowClassName: String, initiatedFlowClassName: String)

    fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: HoldingIdentity,
        cpiId: String,
        args: String,
        platformContext: Map<String, String> = emptyMap()
    ): FlowIoRequestSetup

    fun sessionInitEventReceived(
        flowId: String,
        sessionId: String,
        cpiId: String,
        protocol: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun sessionAckEventReceived(
        flowId: String,
        sessionId: String,
        receivedSequenceNum: Int,
        outOfOrderSeqNums: List<Int> = emptyList()
    ): FlowIoRequestSetup

    fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        outOfOrderSeqNums: List<Int> = emptyList()
    ): FlowIoRequestSetup

    fun sessionCloseEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun sessionErrorEventReceived(
        flowId: String,
        sessionId: String,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun wakeupEventReceived(flowId: String): FlowIoRequestSetup

    fun externalEventReceived(flowId: String, requestId: String, payload: Any): FlowIoRequestSetup

    fun externalEventErrorReceived(
        flowId: String,
        requestId: String,
        errorType: ExternalEventResponseErrorType
    ): FlowIoRequestSetup
}