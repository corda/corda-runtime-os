package net.corda.membership.impl.synchronisation.dummy

import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.SignedGroupParameters
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
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Created for mocking and simplifying membership persistence client functionalities used by the membership services.
 */
interface TestMembershipPersistenceClient : MembershipPersistenceClient {
    fun getPersistedGroupParameters(): SignedGroupParameters?
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipPersistenceClient::class, TestMembershipPersistenceClient::class])
class TestMembershipPersistenceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestMembershipPersistenceClient {
    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    private var persistedGroupParameters: SignedGroupParameters? = null

    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun getPersistedGroupParameters(): SignedGroupParameters? = persistedGroupParameters

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceResult<Unit> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap
    ): MembershipPersistenceResult<Int> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun persistGroupParametersInitialSnapshot(viewOwningIdentity: HoldingIdentity): MembershipPersistenceResult<SignedGroupParameters> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: SignedGroupParameters
    ): MembershipPersistenceResult<SignedGroupParameters> {
        persistedGroupParameters = groupParameters
        return MembershipPersistenceResult.Success(groupParameters)
    }

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo
    ): MembershipPersistenceResult<SignedGroupParameters> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceResult<Unit> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String
    ): MembershipPersistenceResult<MemberInfo> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun setMemberAndRegistrationRequestAsDeclined(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String
    ): MembershipPersistenceResult<Unit> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): MembershipPersistenceResult<Unit> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity, ruleParams: ApprovalRuleParams
    ): MembershipPersistenceResult<ApprovalRuleDetails> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity, ruleId: String, ruleType: ApprovalRuleType
    ): MembershipPersistenceResult<Unit> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override fun generatePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override fun consumePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name,
        preAuthTokenId: UUID
    ) = throw UnsupportedOperationException(UNIMPLEMENTED_FUNCTION)

    override fun revokePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        remarks: String?
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