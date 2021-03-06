package net.corda.flow.testing.context

import net.corda.data.KeyValuePairList
import java.nio.ByteBuffer
import net.corda.data.ExceptionEnvelope
import net.corda.data.identity.HoldingIdentity
import net.corda.data.persistence.Error
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant

interface StepSetup {

    val initiatedIdentityMemberName: MemberX500Name

    fun virtualNode(cpiId: String, holdingId: HoldingIdentity)

    fun cpkMetadata(cpiId: String, cpkId: String, cpkChecksum: SecureHash)

    fun sandboxCpk(cpkFileChecksum: SecureHash)

    fun membershipGroupFor(owningMember: HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity)

    fun flowConfiguration(key:String, value:Any)

    fun initiatingToInitiatedFlow(protocol: String, initiatingFlowClassName: String, initiatedFlowClassName: String)

    fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: HoldingIdentity,
        cpiId: String,
        args: String
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

    fun sessionErrorEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        receivedSequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun wakeupEventReceived(flowId: String): FlowIoRequestSetup

    fun entityResponseSuccessReceived(
        flowId: String,
        requestId: String,
        byteBuffer: ByteBuffer?,
    ): FlowIoRequestSetup

    fun entityResponseErrorReceived(
        flowId: String,
        requestId: String,
        errorType: Error,
        exception: ExceptionEnvelope
    ): FlowIoRequestSetup

    fun cryptoSignResponseReceived(
        flowId: String,
        requestId: String,
        publicKey: PublicKey,
        bytes: ByteArray,
        requestingComponent: String = "Flow Worker",
        requestingTimestamp: Instant = Instant.now(),
        responseTimestamp: Instant = Instant.now(),
        tenantId: String = "tenant",
        otherContext: KeyValuePairList = KeyValuePairList(mutableListOf()),
        exceptionEnvelope: ExceptionEnvelope? = null
    ): FlowIoRequestSetup
}