package net.corda.p2p.linkmanager.integration.stub

import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.util.UUID

class MembershipQueryClientStub : MembershipQueryClient {
    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        statusFilter: List<String>,
    ) = throw UnsupportedOperationException()

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        holdingIdentityFilter: Collection<HoldingIdentity>,
        statusFilter: List<String>,
    ) = throw UnsupportedOperationException()

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ) = throw UnsupportedOperationException()

    override fun queryRegistrationRequests(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
        limit: Int?,
    ) = throw UnsupportedOperationException()

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity) = throw UnsupportedOperationException()

    override fun mutualTlsListAllowedCertificates(
        mgmHoldingIdentity: HoldingIdentity,
    ) = throw UnsupportedOperationException()

    override fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean,
    ) = throw UnsupportedOperationException()

    override fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType,
    ) = throw UnsupportedOperationException()

    override fun queryStaticNetworkInfo(groupId: String) = throw UnsupportedOperationException()

    override val isRunning = true

    override fun start() = Unit

    override fun stop() = Unit
}
