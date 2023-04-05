package net.corda.membership.impl.registration.dummy

import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking
import java.time.Instant
import java.util.UUID

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipPersistenceClient::class])
class TestMembershipPersistenceClientImpl @Activate constructor() : MembershipPersistenceClient {
    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<SignedMemberInfo>,
    ) = MembershipPersistenceResult.success()

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
        version: Long
    ) = MembershipPersistenceResult.success()

    override fun persistGroupParametersInitialSnapshot(
        viewOwningIdentity: HoldingIdentity
    ): MembershipPersistenceResult<InternalGroupParameters> =
        throw UnsupportedOperationException("not implemented for testing")

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: InternalGroupParameters,
    ): MembershipPersistenceResult<InternalGroupParameters> = MembershipPersistenceResult.Success(groupParameters)

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo,
    ): MembershipPersistenceResult<InternalGroupParameters> =
        throw UnsupportedOperationException("not implemented for testing")

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest,
    ): MembershipPersistenceResult<Unit> = MembershipPersistenceResult.success()

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceResult<MemberInfo> = MembershipPersistenceResult.Failure("Unsupported")

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): MembershipPersistenceResult<Unit> = MembershipPersistenceResult.success()

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceResult<Unit> = MembershipPersistenceResult.success()

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceResult<Unit> = MembershipPersistenceResult.success()

    override fun generatePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?,
    ) = MembershipPersistenceResult.success()

    override fun consumePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name,
        preAuthTokenId: UUID
    ) = MembershipPersistenceResult.success()

    override fun revokePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        remarks: String?,
    ): MembershipPersistenceResult<PreAuthToken> = MembershipPersistenceResult.Failure("Unsupported")

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleParams: ApprovalRuleParams,
    ): MembershipPersistenceResult<ApprovalRuleDetails> = MembershipPersistenceResult.Failure("Unsupported")


    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleId: String,
        ruleType: ApprovalRuleType,
    ) = MembershipPersistenceResult.success()

    override fun suspendMember(
        viewOwningIdentity: HoldingIdentity, memberX500Name: MemberX500Name, serialNumber: Long?, reason: String?
    ): MembershipPersistenceResult<Pair<PersistentMemberInfo, InternalGroupParameters?>> =
        MembershipPersistenceResult.Failure("Unsupported")

    override fun activateMember(
        viewOwningIdentity: HoldingIdentity, memberX500Name: MemberX500Name, serialNumber: Long?, reason: String?
    ):MembershipPersistenceResult<Pair<PersistentMemberInfo, InternalGroupParameters?>> =
        MembershipPersistenceResult.Failure("Unsupported")

    override fun updateStaticNetworkInfo(
        info: StaticNetworkInfo
    ): MembershipPersistenceResult<StaticNetworkInfo> = MembershipPersistenceResult.Failure("Unsupported")

    override val isRunning = true

    override fun start() {}

    override fun stop() {}
}
