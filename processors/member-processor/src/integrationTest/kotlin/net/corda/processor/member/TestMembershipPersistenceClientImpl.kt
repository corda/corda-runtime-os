package net.corda.processor.member

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
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
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyAlgorithm
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.records.Record
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.UUID

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipPersistenceClient::class, MembershipQueryClient::class])
@Suppress("TooManyFunctions")
internal class TestMembershipPersistenceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : MembershipPersistenceClient, MembershipQueryClient {

    private val clock = UTCClock()
    private var groupParameters = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
        ),
    )

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<SelfSignedMemberInfo>,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
        version: Long,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun persistGroupParametersInitialSnapshot(viewOwningIdentity: HoldingIdentity) =
        throw NotImplementedError("Not implemented for test service")

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: InternalGroupParameters,
    ): MembershipPersistenceOperation<InternalGroupParameters> = Operation(MembershipPersistenceResult.Success(groupParameters))

    override fun addNotaryToGroupParameters(notary: PersistentMemberInfo) = throw NotImplementedError("Not implemented for test service")

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest,
        create: Boolean,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceOperation<PersistentMemberInfo> =
        Operation(MembershipPersistenceResult.Failure("Unsupported!"))

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
        preAuthTokenId: UUID,
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
        ruleType: ApprovalRuleType,
    ): MembershipPersistenceOperation<Unit> = Operation(MembershipPersistenceResult.success())

    override fun suspendMember(
        viewOwningIdentity: HoldingIdentity,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?,
    ): MembershipPersistenceOperation<Pair<PersistentMemberInfo, InternalGroupParameters?>> = Operation(
        MembershipPersistenceResult.Success(PersistentMemberInfo() to null),
    )

    override fun activateMember(
        viewOwningIdentity: HoldingIdentity,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?,
    ): MembershipPersistenceOperation<Pair<PersistentMemberInfo, InternalGroupParameters?>> = Operation(
        MembershipPersistenceResult.Success(PersistentMemberInfo() to null),
    )

    override fun updateStaticNetworkInfo(info: StaticNetworkInfo): MembershipPersistenceOperation<StaticNetworkInfo> {
        return Operation(MembershipPersistenceResult.Success(info))
    }

    override fun updateGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        newGroupParameters: Map<String, String>,
    ): MembershipPersistenceOperation<InternalGroupParameters> =
        throw NotImplementedError("Not implemented for test service")

    private val persistenceCoordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }
    private val queryCoordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        statusFilter: List<String>,
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> = MembershipQueryResult.Success(emptyList())

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        holdingIdentityFilter: Collection<HoldingIdentity>,
        statusFilter: List<String>,
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> = MembershipQueryResult.Success(emptyList())

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ): MembershipQueryResult<RegistrationRequestDetails?> = MembershipQueryResult.Success(null)

    override fun queryRegistrationRequests(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
        limit: Int?,
    ): MembershipQueryResult<List<RegistrationRequestDetails>> = MembershipQueryResult.Success(emptyList())

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Pair<LayeredPropertyMap, Long>> =
        MembershipQueryResult.Failure("Unsupported")

    override fun mutualTlsListAllowedCertificates(mgmHoldingIdentity: HoldingIdentity): MembershipQueryResult<Collection<String>> =
        MembershipQueryResult.Success(
            emptyList(),
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

    override fun queryStaticNetworkInfo(groupId: String): MembershipQueryResult<StaticNetworkInfo> {
        val (public, private) = KeyPairGenerator.getInstance(
            mgmSigningKeyAlgorithm,
            mgmSigningKeyProvider,
        ).genKeyPair().let { it.public.encoded to it.private.encoded }
        return MembershipQueryResult.Success(
            StaticNetworkInfo(
                groupId,
                groupParameters,
                ByteBuffer.wrap(public),
                ByteBuffer.wrap(private),
                1,
            ),
        )
    }

    override val isRunning = true

    override fun start() {
        persistenceCoordinator.start()
        queryCoordinator.start()
    }

    override fun stop() {
        persistenceCoordinator.stop()
        queryCoordinator.stop()
    }

    private class Operation<T>(private val result: MembershipPersistenceResult<T>) : MembershipPersistenceOperation<T> {
        override fun execute() = result

        override fun createAsyncCommands(): Collection<Record<*, *>> = emptyList()
    }
}
