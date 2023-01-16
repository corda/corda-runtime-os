package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToDeclined
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.data.membership.db.response.command.PersistGroupPolicyResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.layeredpropertymap.toAvro
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MembershipPersistenceClient::class])
class MembershipPersistenceClientImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    configurationReadService: ConfigurationReadService,
    private val memberInfoFactory: MemberInfoFactory,
    clock: Clock,
) : MembershipPersistenceClient, AbstractPersistenceClient(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
    publisherFactory,
    configurationReadService,
    clock,
) {
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
    ) : this(
        coordinatorFactory,
        publisherFactory,
        configurationReadService,
        memberInfoFactory,
        UTCClock(),
    )

    private companion object {
        val logger = contextLogger()
    }

    override val groupName = "membership.db.persistence.client.group"
    override val clientName = "membership.db.persistence.client"

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceResult<Unit> {
        logger.info("Persisting ${memberInfos.size} member info(s).")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistMemberInfo(
                memberInfos.map {
                    PersistentMemberInfo(
                        avroViewOwningIdentity,
                        it.memberProvidedContext.toAvro(),
                        it.mgmProvidedContext.toAvro(),
                    )
                }

            )
        ).execute()
        return when (val failedResponse = result.payload as? PersistenceFailedResponse) {
            null -> MembershipPersistenceResult.success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
    ): MembershipPersistenceResult<Int> {
        logger.info("Persisting group policy.")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistGroupPolicy(groupPolicy.toAvro())
        ).execute()
        return when (val response = result.payload) {
            is PersistGroupPolicyResponse -> MembershipPersistenceResult.Success(response.version)
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(response.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $response")
        }
    }

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: GroupParameters
    ): MembershipPersistenceResult<KeyValuePairList> {
        logger.info("Persisting group parameters.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParameters(groupParameters.toAvro())
        ).execute()
        return when (val response = result.payload) {
            is PersistGroupParametersResponse -> MembershipPersistenceResult.Success(response.groupParameters)
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(response.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $response")
        }
    }

    override fun persistGroupParametersInitialSnapshot(viewOwningIdentity: HoldingIdentity): MembershipPersistenceResult<KeyValuePairList> {
        logger.info("Persisting initial snapshot of group parameters.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParametersInitialSnapshot()
        ).execute()
        return when (val response = result.payload) {
            is PersistGroupParametersResponse -> MembershipPersistenceResult.Success(response.groupParameters)
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(response.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $response")
        }
    }

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo
    ): MembershipPersistenceResult<KeyValuePairList> {
        logger.info("Adding notary to persisted group parameters.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            AddNotaryToGroupParameters(
                PersistentMemberInfo(
                    viewOwningIdentity.toAvro(),
                    notary.memberProvidedContext.toAvro(),
                    notary.mgmProvidedContext.toAvro()
                )
            )
        ).execute()
        return when (val response = result.payload) {
            is PersistGroupParametersResponse -> MembershipPersistenceResult.Success(response.groupParameters)
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(response.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $response")
        }
    }

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceResult<Unit> {
        logger.info("Persisting the member registration request.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistRegistrationRequest(
                registrationRequest.status,
                registrationRequest.requester.toAvro(),
                with(registrationRequest) {
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext,
                        signature,
                    )
                }
            )
        ).execute()
        return when (val failedResponse = result.payload as? PersistenceFailedResponse) {
            null -> MembershipPersistenceResult.success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceResult<MemberInfo> {
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateMemberAndRegistrationRequestToApproved(
                approvedMember.toAvro(),
                registrationRequestId
            )
        ).execute()

        return when (val payload = result.payload) {
            is UpdateMemberAndRegistrationRequestResponse -> MembershipPersistenceResult.Success(
                memberInfoFactory.create(payload.memberInfo)
            )
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(payload.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected result: $payload")
        }
    }

    override fun setMemberAndRegistrationRequestAsDeclined(
        viewOwningIdentity: HoldingIdentity,
        declinedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceResult<Unit> {
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateMemberAndRegistrationRequestToDeclined(
                declinedMember.toAvro(),
                registrationRequestId
            )
        ).execute()

        return when (val payload = result.payload) {
            is UpdateMemberAndRegistrationRequestResponse -> MembershipPersistenceResult.success()
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(payload.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected result: $payload")
        }
    }

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus
    ): MembershipPersistenceResult<Unit> {
        logger.info("Updating the status of a registration request with ID '$registrationId'.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateRegistrationRequestStatus(registrationId, registrationRequestStatus)
        ).execute()
        return when (val failedResponse = result.payload as? PersistenceFailedResponse) {
            null -> MembershipPersistenceResult.success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceResult<Unit> {
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            MutualTlsAddToAllowedCertificates(
                subject
            )
        ).execute()

        return when (val payload = result.payload) {
            null -> MembershipPersistenceResult.success()
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(payload.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected result: $payload")
        }
    }

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceResult<Unit> {
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            MutualTlsRemoveFromAllowedCertificates(
                subject
            )
        ).execute()

        return when (val payload = result.payload) {
            null -> MembershipPersistenceResult.success()
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(payload.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected result: $payload")
        }
    }

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        rule: String,
        ruleType: ApprovalRuleType,
        label: String?
    ): MembershipPersistenceResult<String> {
        val ruleId = UUID.randomUUID().toString()
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistApprovalRule(ruleId, rule, ruleType, label)
        ).execute()
        return when (val payload = result.payload) {
            is PersistApprovalRuleResponse -> MembershipPersistenceResult.Success(payload.ruleId)
            is PersistenceFailedResponse -> MembershipPersistenceResult.Failure(payload.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $payload")
        }
    }

    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity, ruleId: String
    ): MembershipPersistenceResult<Unit> {
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            DeleteApprovalRule(ruleId)
        ).execute()
        return when (val failedResponse = result.payload as? PersistenceFailedResponse) {
            null -> MembershipPersistenceResult.success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }
}
