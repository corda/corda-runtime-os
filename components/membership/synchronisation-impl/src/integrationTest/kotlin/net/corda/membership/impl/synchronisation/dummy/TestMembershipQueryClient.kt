package net.corda.membership.impl.synchronisation.dummy

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.RegistrationStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.SignedMemberInfo
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Created for mocking and simplifying membership query client functionalities used by the membership services.
 */
interface TestMembershipQueryClient : MembershipQueryClient {
    fun loadMembers(memberList: List<MemberInfo>)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipQueryClient::class, TestMembershipQueryClient::class])
class TestMembershipQueryClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestMembershipQueryClient {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
        private lateinit var members: List<SignedMemberInfo>
    }

    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<MembershipQueryClient>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun loadMembers(memberList: List<MemberInfo>) {
        members = memberList.map {
            SignedMemberInfo(
                it,
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(it.holdingIdentity.x500Name.toString().toByteArray()),
                    ByteBuffer.wrap(it.holdingIdentity.x500Name.toString().toByteArray()),
                ),
                CryptoSignatureSpec("", null, null)
            )
        }
    }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        statusFilter: List<String>,
    ): MembershipQueryResult<Collection<SignedMemberInfo>> = MembershipQueryResult.Success(members)

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        holdingIdentityFilter: Collection<HoldingIdentity>,
        statusFilter: List<String>,
    ): MembershipQueryResult<Collection<SignedMemberInfo>> {
        val result = mutableListOf<SignedMemberInfo>()
        holdingIdentityFilter.forEach { id ->
            result.addAll(members.filter { it.memberInfo.holdingIdentity == id })
        }
        return MembershipQueryResult.Success(result)
    }

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String
    ): MembershipQueryResult<RegistrationRequestDetails?> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun queryRegistrationRequests(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
        limit: Int?,
    ): MembershipQueryResult<List<RegistrationRequestDetails>> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Pair<LayeredPropertyMap, Long>> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType
    ): MembershipQueryResult<Collection<ApprovalRuleDetails>> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun queryStaticNetworkInfo(groupId: String): MembershipQueryResult<StaticNetworkInfo> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun mutualTlsListAllowedCertificates(
        mgmHoldingIdentity: HoldingIdentity,
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("${this::class.java.simpleName} starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("${this::class.java.simpleName} stopping.")
        coordinator.stop()
    }
}