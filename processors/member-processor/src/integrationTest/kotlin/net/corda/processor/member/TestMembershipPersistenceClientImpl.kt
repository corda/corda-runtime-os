package net.corda.processor.member

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyAlgorithm
import net.corda.membership.network.writer.staticnetwork.StaticNetworkUtils.mgmSigningKeyProvider
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
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
internal class TestMembershipPersistenceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : MembershipPersistenceClient, MembershipQueryClient {

    private val clock = UTCClock()
    private var groupParameters = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "1"),
            KeyValuePair(MPV_KEY, "5000"),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    )

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>,
    ) = MembershipPersistenceResult.success()

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
        version: Long
    ) = MembershipPersistenceResult.success()

    override fun persistGroupParametersInitialSnapshot(viewOwningIdentity: HoldingIdentity) =
        throw NotImplementedError("Not implemented for test service")

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: InternalGroupParameters,
    ) = MembershipPersistenceResult.Success(groupParameters)

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo,
    ) = throw NotImplementedError("Not implemented for test service")

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest,
    ) = MembershipPersistenceResult.success()

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ) = MembershipPersistenceResult.Failure<MemberInfo>("Unsupported!")

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ) = MembershipPersistenceResult.success()

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ) = MembershipPersistenceResult.success()

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ) = MembershipPersistenceResult.success()

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
    ) = MembershipPersistenceResult.Success(PreAuthToken())

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleParams: ApprovalRuleParams,
    ) = MembershipPersistenceResult.Success(ApprovalRuleDetails())

    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleId: String,
        ruleType: ApprovalRuleType
    ) = MembershipPersistenceResult.success()

    override fun updateStaticNetworkInfo(info: StaticNetworkInfo): MembershipPersistenceResult<StaticNetworkInfo> {
        return MembershipPersistenceResult.Success(info)
    }

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
        limit: Int?,
    ): MembershipQueryResult<List<RegistrationRequestStatus>> = MembershipQueryResult.Success(emptyList())

    override fun queryMembersSignatures(
        viewOwningIdentity: HoldingIdentity,
        holdingsIdentities: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Map<HoldingIdentity, CryptoSignatureWithKey>> = MembershipQueryResult.Success(emptyMap())

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Pair<LayeredPropertyMap, Long>> =
        MembershipQueryResult.Failure("Unsupported")

    override fun mutualTlsListAllowedCertificates(mgmHoldingIdentity: HoldingIdentity): MembershipQueryResult<Collection<String>> =
        MembershipQueryResult.Success(
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

    override fun queryStaticNetworkInfo(groupId: String): MembershipQueryResult<StaticNetworkInfo> {
        val (public, private) = KeyPairGenerator.getInstance(
            mgmSigningKeyAlgorithm, mgmSigningKeyProvider
        ).genKeyPair().let { it.public.encoded to it.private.encoded }
        return MembershipQueryResult.Success(
            StaticNetworkInfo(
                groupId,
                groupParameters,
                ByteBuffer.wrap(public),
                ByteBuffer.wrap(private),
                1,
            )
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
}
