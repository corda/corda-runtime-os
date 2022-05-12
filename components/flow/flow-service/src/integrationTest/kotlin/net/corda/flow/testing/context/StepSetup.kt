package net.corda.flow.testing.context

import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

interface StepSetup {

    val initiatedIdentityMemberName: MemberX500Name

    fun virtualNode(cpiId: String, holdingId: HoldingIdentity)

    fun cpkMetadata(cpiId: String, cpkId: String)

    fun sandboxCpk(cpkId: String)

    fun membershipGroupFor(owningMember: HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity)

    fun flowConfiguration(key:String, value:Any)

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
        sequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null,
        receivedSequenceNum: Int? = null
    ): FlowIoRequestSetup

    fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null,
        receivedSequenceNum: Int? = null
    ): FlowIoRequestSetup

    fun wakeupEventReceived(flowId: String): FlowIoRequestSetup
}