package net.corda.processor.member

import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.time.Instant
import java.util.UUID

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipPersistenceClient::class, MembershipQueryClient::class])
internal class TestMembershipPersistenceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : MembershipPersistenceClient, MembershipQueryClient {
    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>,
    ) : MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
    ) : MembershipPersistenceOperation<Int> = Operation(MembershipPersistenceResult.Success(1))

    override fun persistGroupParametersInitialSnapshot(viewOwningIdentity: HoldingIdentity)
     : MembershipPersistenceOperation<KeyValuePairList> =
        Operation(MembershipPersistenceResult.Success(KeyValuePairList()))

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: GroupParameters,
    ) : MembershipPersistenceOperation<GroupParameters> = Operation(MembershipPersistenceResult.Success(groupParameters))

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo,
    ) : MembershipPersistenceOperation<KeyValuePairList> = Operation(MembershipPersistenceResult.Success(KeyValuePairList()))

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ) : MembershipPersistenceOperation<MemberInfo> = Operation(MembershipPersistenceResult.Failure<MemberInfo>("Unsupported!"))

    override fun setMemberAndRegistrationRequestAsDeclined(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun generatePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun consumePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name,
        preAuthTokenId: UUID
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun revokePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        remarks: String?,
    ): MembershipPersistenceOperation<PreAuthToken> = Operation(MembershipPersistenceResult.Success(PreAuthToken()))

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleParams: ApprovalRuleParams,
    ): MembershipPersistenceOperation<ApprovalRuleDetails> = Operation(MembershipPersistenceResult.Success(ApprovalRuleDetails()))

    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleId: String,
        ruleType: ApprovalRuleType
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    private val persistenceCoordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>()
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    private val queryCoordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<MembershipQueryClient>()
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun queryMemberInfo(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Collection<MemberInfo>> =
        MembershipQueryResult.Success(emptyList())

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        queryFilter: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Collection<MemberInfo>> = MembershipQueryResult.Success(emptyList())

    override fun queryRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ): MembershipQueryResult<RegistrationRequestStatus?> = MembershipQueryResult.Success(null)

    override fun queryRegistrationRequestsStatus(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
    ): MembershipQueryResult<List<RegistrationRequestStatus>> = MembershipQueryResult.Success(emptyList())

    override fun queryMembersSignatures(
        viewOwningIdentity: HoldingIdentity,
        holdingsIdentities: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Map<HoldingIdentity, CryptoSignatureWithKey>> = MembershipQueryResult.Success(emptyMap())

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<LayeredPropertyMap> =
        MembershipQueryResult.Failure("Unsupported")

    override fun mutualTlsListAllowedCertificates(mgmHoldingIdentity: HoldingIdentity): MembershipQueryResult<Collection<String>> = MembershipQueryResult.Success(
        emptyList()
    )

    override fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean,
    ): MembershipQueryResult<List<PreAuthToken>> = MembershipQueryResult.Success(emptyList())

    override fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType,
    ): MembershipQueryResult<Collection<ApprovalRuleDetails>> = MembershipQueryResult.Success(emptyList())

    override val isRunning = true

    override fun start() {
        persistenceCoordinator.start()
        queryCoordinator.start()
    }

    override fun stop() {
        persistenceCoordinator.stop()
        queryCoordinator.stop()
    }

    private class Operation<T>(private val result: MembershipPersistenceResult<T>): MembershipPersistenceOperation<T> {
        override fun execute() =  result

        override fun createAsyncCommands(): Collection<Record<*, *>> = emptyList()

    }
}
