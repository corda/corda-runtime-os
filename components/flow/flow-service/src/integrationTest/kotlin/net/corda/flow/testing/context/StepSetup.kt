package net.corda.flow.testing.context

import net.corda.data.flow.event.external.ExternalEventResponseErrorType
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.VirtualNodeInfo
import java.time.Instant

@Suppress("TooManyFunctions")
interface StepSetup {

    val initiatedIdentityMemberName: MemberX500Name

    val initiatingIdentityMemberName: MemberX500Name


    @Suppress("LongParameterList")
    fun virtualNode(
        cpiId: String,
        holdingId: HoldingIdentity,
        flowP2pOperationalStatus: OperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
        flowStartOperationalStatus: OperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
        flowOperationalStatus: OperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
        vaultDbOperationalStatus: OperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE
    )

    fun cpkMetadata(cpiId: String, cpkId: String, cpkChecksum: SecureHash)

    fun sandboxCpk(cpkFileChecksum: SecureHash)

    fun membershipGroupFor(owningMember: HoldingIdentity)

    fun sessionInitiatingIdentity(initiatingIdentity: HoldingIdentity)

    fun sessionInitiatedIdentity(initiatedIdentity: HoldingIdentity)

    fun flowConfiguration(key: String, value: Any)

    fun initiatingToInitiatedFlow(protocol: String, initiatingFlowClassName: String, initiatedFlowClassName: String)

    @Suppress("LongParameterList")
    fun startFlowEventReceived(
        flowId: String,
        requestId: String,
        holdingId: HoldingIdentity,
        cpiId: String,
        args: String,
        platformContext: Map<String, String> = emptyMap()
    ): FlowIoRequestSetup

    @Suppress("LongParameterList")
    fun sessionCounterpartyInfoRequestReceived(
        flowId: String,
        sessionId: String,
        cpiId: String,
        protocol: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null,
        requireClose: Boolean = true
    ): FlowIoRequestSetup

    @Suppress("LongParameterList")
    fun sessionDataEventReceived(
        flowId: String,
        sessionId: String,
        data: ByteArray,
        sequenceNum: Int,
        sessionInit: SessionInit? = null,
        timestamp: Instant = Instant.now()
    ): FlowIoRequestSetup

    fun sessionCounterpartyInfoResponseReceived(
        flowId: String,
        sessionId: String,
    ): FlowIoRequestSetup

    fun sessionCloseEventReceived(
        flowId: String,
        sessionId: String,
        sequenceNum: Int,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun sessionErrorEventReceived(
        flowId: String,
        sessionId: String,
        initiatingIdentity: HoldingIdentity? = null,
        initiatedIdentity: HoldingIdentity? = null
    ): FlowIoRequestSetup

    fun externalEventReceived(flowId: String, requestId: String, payload: Any): FlowIoRequestSetup

    fun externalEventErrorReceived(
        flowId: String,
        requestId: String,
        errorType: ExternalEventResponseErrorType
    ): FlowIoRequestSetup

    fun resetFlowFiberCache()
}