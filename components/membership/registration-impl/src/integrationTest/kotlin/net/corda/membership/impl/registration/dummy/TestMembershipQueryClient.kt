package net.corda.membership.impl.registration.dummy

import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.UUID

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipQueryClient::class])
class TestMembershipQueryClient @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : MembershipQueryClient {
    private val coordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<MembershipQueryClient>()
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        statusFilter: List<String>
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        holdingIdentityFilter: Collection<HoldingIdentity>,
        statusFilter: List<String>
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ): MembershipQueryResult<RegistrationRequestDetails?> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryRegistrationRequests(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
        limit: Int?,
    ): MembershipQueryResult<List<RegistrationRequestDetails>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Pair<LayeredPropertyMap, Long>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun mutualTlsListAllowedCertificates(mgmHoldingIdentity: HoldingIdentity): MembershipQueryResult<Collection<String>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean,
    ): MembershipQueryResult<List<PreAuthToken>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType,
    ): MembershipQueryResult<Collection<ApprovalRuleDetails>> {
        return MembershipQueryResult.Failure("oops")
    }

    override fun queryStaticNetworkInfo(groupId: String): MembershipQueryResult<StaticNetworkInfo> {
        return MembershipQueryResult.Failure("oops")
    }

    override val isRunning = true

    override fun start() {
        coordinator.start()
    }

    override fun stop() = Unit
}
